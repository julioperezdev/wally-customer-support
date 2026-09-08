# State separado para recursos no productivos de WCS.
# El bucket debe ser validado en la cuenta objetivo antes del primer init real.
terraform {
  backend "s3" {
    bucket       = "tesis-dev-terraform-state-us-east-1"
    key          = "wally-customer-support/environments/test/terraform.tfstate"
    region       = "us-east-1"
    encrypt      = true
    use_lockfile = true
  }
}
