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

El módulo `appconfig` admite `appconfig_configuration_content` opcional. Al
proveerlo crea una versión hosted JSON y un deployment all-at-once; el JSON
debe contener configuración no sensible y referencias a Secrets Manager, nunca
tokens o passwords. Si queda en `null`, Terraform crea únicamente la
application, environment y profile para que el contenido pueda cargarse por
otro proceso aprobado.

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
habilita App Runner, el VPC connector. Los valores sensibles permanecen en
Secrets Manager; nunca se escriben en `terraform.tfvars`, Jira, Confluence o
logs.

`backend_create_service` queda en `false` hasta que exista una imagen inicial,
la carga de configuración AWS esté implementada y se haya verificado la red.
No ejecutar `terraform apply` sin revisar el plan guardado: cualquier
`destroy` o reemplazo inesperado bloquea la operación.

## Estado del diseño

Esta carpeta es la base de infraestructura y CI/CD. No implica que exista un
deployment productivo ni que el servicio App Runner esté creado. La creación
de recursos, la configuración real de AWS y la activación de cada integración
se documentan con evidencia en Jira y Confluence.
