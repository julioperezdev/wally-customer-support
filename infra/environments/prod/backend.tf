# Este bucket/key se toma del patrón existente de tesis-dev.
# Verificar cuenta, región, permisos y ownership antes de `terraform init`.
terraform {
  backend "s3" {
    bucket       = "tesis-dev-terraform-state-us-east-1"
    key          = "wally-customer-support/environments/prod/terraform.tfstate"
    region       = "us-east-1"
    encrypt      = true
    use_lockfile = true
  }
}
