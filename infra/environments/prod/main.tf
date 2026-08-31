data "aws_caller_identity" "current" {}

data "aws_db_instance" "shared" {
  count = var.shared_rds_instance_identifier == null ? 0 : 1

  db_instance_identifier = var.shared_rds_instance_identifier
}

data "aws_secretsmanager_secret" "shared_rds" {
  count = var.shared_rds_secret_arn == null ? 0 : 1

  arn = var.shared_rds_secret_arn
}

locals {
  common_tags = {
    Project     = var.project_name
    Environment = var.environment
    ManagedBy   = "terraform"
  }

  runtime_environment_variables = merge(
    {
      AWS_REGION                    = var.aws_region
      SPRING_PROFILES_ACTIVE        = "production"
      AWS_APPCONFIG_APPLICATION     = module.appconfig.application_id
      AWS_APPCONFIG_ENVIRONMENT     = module.appconfig.environment_id
      AWS_APPCONFIG_PROFILE         = module.appconfig.configuration_profile_id
      AWS_SECRETS_MANAGER_SECRET_ID = module.runtime_secrets.secret_name
      AWS_SHARED_RDS_SECRET_ID      = coalesce(var.shared_rds_secret_arn, "")
    },
    var.backend_runtime_environment_variables
  )

  runtime_secret_arns = setunion(
    toset(values(var.backend_runtime_environment_secrets)),
    toset([module.runtime_secrets.secret_arn]),
    var.shared_rds_secret_arn == null ? toset([]) : toset([var.shared_rds_secret_arn])
  )
}

module "appconfig" {
  source = "../../modules/appconfig"

  application_name = "${var.project_name}-${var.environment}"
  environment_name = var.environment
  profile_name     = "runtime"
  tags             = local.common_tags
}

module "runtime_secrets" {
  source = "../../modules/runtime-secrets"

  name        = coalesce(var.runtime_secret_name, "wcs/${var.environment}/runtime")
  description = "WCS runtime secret container; values are written outside Terraform."
  tags        = local.common_tags
}

module "backend_apprunner" {
  source = "../../modules/backend-apprunner"

  project_name                  = var.project_name
  environment                   = var.environment
  ecr_repository_name           = "${var.project_name}-${var.environment}-backend"
  create_service                = var.backend_create_service
  image_tag                     = var.backend_image_tag
  cpu                           = var.backend_cpu
  memory                        = var.backend_memory
  runtime_environment_variables = local.runtime_environment_variables
  runtime_environment_secrets   = var.backend_runtime_environment_secrets
  runtime_secret_arns           = local.runtime_secret_arns
  vpc_connector_arn             = var.backend_vpc_connector_arn
  enable_bedrock_access         = var.enable_bedrock_access
  bedrock_model_arns            = var.bedrock_model_arns
  tags                          = local.common_tags
}

module "github_backend_deploy" {
  count  = var.existing_github_oidc_provider_arn == null ? 0 : 1
  source = "../../modules/github-backend-deploy"

  project_name                  = var.project_name
  environment                   = var.environment
  github_repository             = var.github_repository
  github_oidc_provider_arn      = var.existing_github_oidc_provider_arn
  ecr_repository_arn            = module.backend_apprunner.ecr_repository_arn
  apprunner_service_arn         = module.backend_apprunner.apprunner_service_arn
  apprunner_ecr_access_role_arn = module.backend_apprunner.apprunner_ecr_access_role_arn
  tags                          = local.common_tags
}
