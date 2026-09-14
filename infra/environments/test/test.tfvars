# Versioned non-secret Terraform configuration for the isolated test stack.

project_name               = "wally-customer-support"
environment                = "test"
aws_region                 = "us-east-1"
appconfig_application_name = "wally-customer-support"
appconfig_profile_name     = "runtime"

backend_create_service = false
backend_image_tag      = "bootstrap"

existing_github_oidc_provider_arn = "arn:aws:iam::438225605070:oidc-provider/token.actions.githubusercontent.com"
github_repository_owner_id        = "67208692"
github_repository_id              = "1351752798"
