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
- Un rol IAM dedicado para que GitHub Actions ejecute Terraform sobre el stack WCS.
- AWS AppConfig para configuración no sensible versionable.
- AWS Secrets Manager para el contenedor de secrets de WCS y la referencia al
  secret existente del RDS compartido.
- Un secret separado `wcs/{environment}/telegram` para el bot de Telegram y el
  secret de validación del webhook.
- RDS PostgreSQL existente de `tesis-dev`, consumido como dependencia externa.
- Bedrock como permiso opcional del runtime, sin habilitarlo por defecto.

No se crean VPC, subnets, security groups ni otra instancia RDS en este stack.
El `backend_vpc_connector_arn` debe apuntar al conector existente que tenga
ruta al RDS compartido, una vez que ese recurso sea confirmado.

El usuario de base de datos que ejecute Flyway debe poder crear el schema
`wcs` y sus tablas, o un administrador debe crearlo previamente y otorgar el
ownership/permisos necesarios. Terraform no intenta modificar permisos del
RDS compartido.

Terraform crea además secretos separados para `wcs/{environment}/database`,
`wcs/{environment}/whatsapp` y `wcs/{environment}/telegram`, con valores
explícitamente falsos para bootstrap.
Esos valores tienen `ignore_changes` para que puedas reemplazarlos en la
consola de Secrets Manager sin que el siguiente `terraform apply` los restaure.
No se debe habilitar App Runner mientras sigan presentes.

El módulo `appconfig` usa una aplicación estable (`wally-customer-support`) y
un environment por despliegue (`dev`, `test` o `prod`). Recibe por defecto un
JSON hosted falso con las claves Spring y referencias a esos tres secrets, y
crea un deployment all-at-once.
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
3. En Secrets Manager, editar `wcs/prod/telegram` con `bot-token` y
   `webhook-secret-token` reales si se habilita ese canal.
4. En AppConfig, reemplazar los valores `REPLACE_ME_*`, cambiar
   `wcs.whatsapp.adapter` a `meta` cuando corresponda y desplegar una nueva
   versión. Para Telegram, cambiar `wcs.telegram.enabled` a `true` y
   `wcs.telegram.adapter` a `telegram` sólo después de cargar el secret.
5. Verificar que `backend_create_service` siga en `false` hasta completar
   conectividad al RDS, permisos IAM, imagen y smoke test.

Los valores fake son deliberados y no permiten una operación productiva. La
configuración manual de consola queda preservada por `ignore_changes`; no
agregar valores reales a `terraform.tfvars`, al repositorio o a los outputs.

## Rol de Terraform para GitHub Actions

Cuando `existing_github_oidc_provider_arn` está configurado, este stack crea el
rol `wally-customer-support-prod-github-terraform-deploy` y expone su ARN en el
output `terraform_github_deploy_role_arn`. El rol usa el mismo proveedor OIDC
de GitHub que puede existir en la cuenta por `tesis-dev`, pero su trust policy
acepta únicamente el repositorio WCS y el Environment `production`. Para
repositorios con el subject OIDC inmutable de GitHub se deben configurar
`github_repository_owner_id` y `github_repository_id`; son identificadores
públicos de GitHub, no secretos.

El permiso está limitado al state de WCS, AppConfig, los secrets con prefijo
`wcs/prod/`, ECR, App Runner, lectura del RDS compartido e IAM de los roles
WCS. Incluye un deny explícito para borrar recursos del stack. Si la cuenta
dispone de una permissions boundary, se puede indicar mediante
`terraform_permissions_boundary_arn`.

El rol no puede auto-crear su propia primera credencial: el primer apply que
lo crea debe ejecutarse con un principal de bootstrap autorizado y con un
plan revisado. Una vez creado, su ARN se configura como el secret
`AWS_TERRAFORM_ROLE_ARN` del Environment `production` y los siguientes plan/apply
se ejecutan desde GitHub Actions.

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
