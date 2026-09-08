# Bootstrap del rol Terraform de `test`

Este root existe únicamente para crear el rol OIDC que GitHub Actions necesita
para administrar el ambiente `test`. No administra AppConfig, Secrets Manager,
ECR, App Runner, RDS ni el state del ambiente; sólo crea el rol, su policy
inline, la policy de Knowledge Base y el attachment definidos por el módulo
`github-terraform-deploy`.

El bootstrap debe ejecutarse con un principal AWS autorizado para crear IAM.
No se ejecuta con el rol Terraform productivo de WCS: ese rol está limitado a
los recursos y al state de `prod`.

## Preparar y revisar

Desde la raíz del repositorio:

```bash
cp infra/bootstrap/test/terraform.tfvars.example \
  infra/bootstrap/test/terraform.tfvars

# Completar sólo los IDs públicos de GitHub y confirmar el OIDC provider.
terraform -chdir=infra/bootstrap/test init
terraform -chdir=infra/bootstrap/test fmt -check -recursive
terraform -chdir=infra/bootstrap/test validate
terraform -chdir=infra/bootstrap/test plan \
  -input=false \
  -var-file=terraform.tfvars \
  -out=bootstrap.tfplan
```

El plan esperado crea únicamente un rol `wally-customer-support-test-*`, su
policy inline, una policy administrada para la fuente de Knowledge Base y el
attachment. Debe detenerse la operación ante cualquier recurso fuera de ese
conjunto, cualquier `destroy` o cualquier reemplazo.

## Aplicar y entregar el rol a GitHub

Después de revisar el plan, la aplicación es explícita:

```bash
terraform -chdir=infra/bootstrap/test apply \
  -input=false \
  bootstrap.tfplan

terraform -chdir=infra/bootstrap/test output terraform_role_arn
```

Configurar el ARN obtenido como secret `AWS_TERRAFORM_ROLE_ARN` del GitHub
Environment `test`. El Environment también debe tener:

* variable `AWS_REGION=us-east-1`;
* secret `TERRAFORM_VARS` con las variables HCL revisadas de
  `infra/environments/test`, sin passwords, tokens ni valores de secretos.

El state del bootstrap es local y debe conservarse de forma segura hasta que
la propiedad de los recursos IAM quede definida. No se debe subir
`terraform.tfstate`, `terraform.tfplan` ni `terraform.tfvars` al repositorio.

Luego de configurar GitHub, el primer paso remoto es ejecutar el workflow
`Terraform` con `target_environment=test` y `action=plan`. El `apply` del root
`infra/environments/test` requiere un plan revisado; este bootstrap no lo
ejecuta automáticamente.

## Rollback

Antes de configurar GitHub, eliminar la configuración incompleta y conservar el
plan como evidencia. No borrar el rol creado mientras GitHub pueda estar
usándolo. Cualquier eliminación posterior requiere un plan específico y una
revisión separada; este README no autoriza `destroy`.
