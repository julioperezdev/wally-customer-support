# Infraestructura AWS — WCS

La infraestructura de Wally Customer Support sigue el patrón de `tesis-dev`,
pero tiene una frontera importante: WCS **no administra ni crea el RDS de
`tesis-dev`**. La aplicación consume esa instancia mediante data sources y
Flyway crea únicamente el schema lógico `wcs`.

## Servicios preparados

- Amazon ECR para imágenes inmutables del backend.
- AWS App Runner como runtime del monolito modular, desactivado por defecto
  hasta completar el contrato de configuración, red y secrets.
- IAM/OIDC para que GitHub Actions despliegue sin access keys.
- AWS AppConfig para configuración no sensible versionable.
- AWS Secrets Manager para el contenedor de secrets de WCS y la referencia al
  secret existente del RDS compartido.
- RDS PostgreSQL existente de `tesis-dev`, consumido como dependencia externa.
- Bedrock como permiso opcional del runtime, sin habilitarlo por defecto.

No se crean VPC, subnets, security groups ni otra instancia RDS en este stack.
El `backend_vpc_connector_arn` debe apuntar al conector existente que tenga
ruta al RDS compartido, una vez que ese recurso sea confirmado.

El usuario de base de datos que ejecute Flyway debe poder crear el schema
`wcs` y sus tablas, o un administrador debe crearlo previamente y otorgar el
ownership/permisos necesarios. Terraform no intenta modificar permisos del
RDS compartido.

Terraform crea además secretos separados para `wcs/{environment}/database` y
`wcs/{environment}/whatsapp`, con valores explícitamente falsos para bootstrap.
Esos valores tienen `ignore_changes` para que puedas reemplazarlos en la
consola de Secrets Manager sin que el siguiente `terraform apply` los restaure.
No se debe habilitar App Runner mientras sigan presentes.

El módulo `appconfig` recibe por defecto un JSON hosted falso con las claves
Spring y referencias a esos dos secrets, y crea un deployment all-at-once.
`appconfig_configuration_content` permite reemplazar ese documento por otro
que contenga sólo configuración no sensible y referencias a Secrets Manager,
nunca tokens o passwords. La versión hosted y el deployment también ignoran
cambios posteriores hechos en la consola, por lo que una operación manual no
será revertida por Terraform.

El flujo posterior al primer `apply` es:

1. En Secrets Manager, editar `wcs/prod/database` con `jdbc_url`, `username` y
   `password` reales, o cambiar en AppConfig la referencia al secret existente
   de `tesis-dev`.
2. En Secrets Manager, editar `wcs/prod/whatsapp` con `access-token`,
   `verify-token` y `app-secret` reales.
3. En AppConfig, reemplazar los valores `REPLACE_ME_*`, cambiar
   `wcs.whatsapp.adapter` a `meta` cuando corresponda y desplegar una nueva
   versión.
4. Verificar que `backend_create_service` siga en `false` hasta completar
   conectividad al RDS, permisos IAM, imagen y smoke test.

Los valores fake son deliberados y no permiten una operación productiva. La
configuración manual de consola queda preservada por `ignore_changes`; no
agregar valores reales a `terraform.tfvars`, al repositorio o a los outputs.

## Estado remoto

El backend conserva el bucket de estado existente de `tesis-dev`, pero usa una
key separada para que cada stack sea dueño únicamente de sus recursos:

```text
s3://tesis-dev-terraform-state-us-east-1/
  wally-customer-support/environments/prod/terraform.tfstate
```

Antes de ejecutar `terraform init` con backend real se debe verificar en la
cuenta destino: `aws sts get-caller-identity`, región, existencia del bucket,
permisos y que la key no esté siendo usada por otro stack. Si el entorno real
requiere otro bucket, se cambia `backend.tf` antes de inicializar.

## Uso seguro

La validación local no necesita AWS ni backend remoto:

```bash
terraform -chdir=infra/environments/prod init -backend=false
terraform -chdir=infra/environments/prod fmt -check -recursive
terraform -chdir=infra/environments/prod validate
```

Para un plan real hay que completar un `terraform.tfvars` local con el
identificador del RDS, ARN del secret del RDS, ARN del proveedor OIDC y, si se
habilita App Runner, el VPC connector. En CI, ese mismo contenido se configura
como el secret `TERRAFORM_VARS` del Environment `production`, mientras que
`AWS_TERRAFORM_ROLE_ARN` apunta a un rol AWS preaprobado con OIDC. Los valores
sensibles permanecen en Secrets Manager; nunca se escriben en
`terraform.tfvars`, `TERRAFORM_VARS`, Jira, Confluence o logs.

El workflow `.github/workflows/terraform.yml` se ejecuta desde GitHub Actions
con `action=plan` o `action=apply`. `apply` requiere ejecutarse desde `main`,
`confirm_apply=true` y aprobación del Environment `production`. Primero se
debe revisar el resumen del plan; la automatización bloquea cualquier
destrucción o reemplazo y no expone el plan completo en los logs.

`backend_create_service` queda en `false` hasta que exista una imagen inicial,
la carga de configuración AWS esté implementada y se haya verificado la red.
No ejecutar `terraform apply` sin revisar el plan guardado: cualquier
`destroy` o reemplazo inesperado bloquea la operación.

## Estado del diseño

Esta carpeta es la base de infraestructura y CI/CD. No implica que exista un
deployment productivo ni que el servicio App Runner esté creado. La creación
de recursos, la configuración real de AWS y la activación de cada integración
se documentan con evidencia en Jira y Confluence.
