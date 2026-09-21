# Versioned production Terraform configuration.
# This file contains reviewed infrastructure references only.
# Runtime credentials remain in AWS Secrets Manager.

aws_region   = "us-east-1"
project_name = "wally-customer-support"
environment  = "prod"

github_repository                 = "julioperezdev/wally-customer-support"
github_repository_owner_id        = "67208692"
github_repository_id              = "1351752798"
existing_github_oidc_provider_arn = "arn:aws:iam::438225605070:oidc-provider/token.actions.githubusercontent.com"

shared_rds_instance_identifier = "tesis-dev-prod"
shared_rds_secret_arn          = "arn:aws:secretsmanager:us-east-1:438225605070:secret:tesis-dev-prod/rds-hCy98m"
database_schema                = "wcs"

# App Runner is already part of the production stack. The backend deployment
# workflow updates its immutable image independently of Terraform.
backend_create_service    = true
backend_image_tag         = "bootstrap"
backend_egress_type       = "DEFAULT"
backend_vpc_connector_arn = null

backend_runtime_environment_variables = {
  AWS_REGION = "us-east-1"
}
backend_runtime_environment_secrets = {}

enable_appconfig_management = false

enable_bedrock_access = true
bedrock_model_arns = [
  "arn:aws:bedrock:us-east-1::foundation-model/openai.gpt-oss-20b-1:0",
  "arn:aws:bedrock:*::inference-profile/us.openai.gpt-5.6-luna",
  "arn:aws:bedrock:*::foundation-model/openai.gpt-5.6-luna"
]
bedrock_prompt_arns   = []
appconfig_secret_arns = []

cognito_domain_prefix        = null
cognito_callback_urls        = ["http://localhost:5173/auth/callback"]
cognito_logout_urls          = ["http://localhost:5173/"]
cognito_enable_password_auth = false
cognito_deletion_protection  = "ACTIVE"

# WCS-119 private media bucket. The exact ngrok origin is temporary and should
# be removed when the backoffice is served from its permanent HTTPS origin.
backoffice_media_enabled     = true
backoffice_media_bucket_name = null
backoffice_media_cors_allowed_origins = [
  "http://localhost:5173",
  "https://constable-daycare-sizzling.ngrok-free.dev"
]

terraform_permissions_boundary_arn = null
