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

  fake_database_secret_json = jsonencode({
    jdbc_url = "jdbc:postgresql://REPLACE_ME:5432/wcs"
    username = "REPLACE_ME_DATABASE_USERNAME"
    password = "REPLACE_ME_DATABASE_PASSWORD"
  })

  fake_whatsapp_secret_json = jsonencode({
    "access-token" = "REPLACE_ME_WHATSAPP_ACCESS_TOKEN"
    "verify-token" = "REPLACE_ME_WHATSAPP_VERIFY_TOKEN"
    "app-secret"   = "REPLACE_ME_META_APP_SECRET"
  })

  fake_telegram_secret_json = jsonencode({
    "bot-token"            = "REPLACE_ME_TELEGRAM_BOT_TOKEN"
    "webhook-secret-token" = "REPLACE_ME_TELEGRAM_WEBHOOK_SECRET"
  })

  fake_appconfig_configuration = jsonencode({
    "wcs.whatsapp.adapter"                                   = "mock"
    "wcs.whatsapp.graph-api-version"                         = "v25.0"
    "wcs.whatsapp.graph-api-base-url"                        = "https://graph.facebook.com"
    "wcs.whatsapp.phone-number-id"                           = "REPLACE_ME_PHONE_NUMBER_ID"
    "wcs.whatsapp.business-account-id"                       = "REPLACE_ME_BUSINESS_ACCOUNT_ID"
    "wcs.whatsapp.allowed-recipient"                         = ""
    "wcs.telegram.enabled"                                   = false
    "wcs.telegram.adapter"                                   = "disabled"
    "wcs.telegram.api-base-url"                              = "https://api.telegram.org"
    "wcs.telegram.allowed-chat-id"                           = ""
    "wcs.telegram.connect-timeout"                           = "PT2S"
    "wcs.telegram.read-timeout"                              = "PT5S"
    "wcs.ai.provider"                                        = "mock"
    "wcs.ai.model"                                           = "llm.mock.v1"
    "wcs.ai.region"                                          = var.aws_region
    "wcs.rag.provider"                                       = "bedrock-kb"
    "wcs.rag.max-results"                                    = 5
    "wcs.rag.knowledge-base-id"                              = module.wcs_knowledge_base.knowledge_base_id
    "wcs.rag.region"                                         = var.aws_region
    "wcs.outbox.max-attempts"                                = 3
    "wcs.external-config.secrets-manager.database-secret-id" = module.database_secrets.secret_name
    "wcs.external-config.secrets-manager.whatsapp-secret-id" = module.whatsapp_secrets.secret_name
    "wcs.external-config.secrets-manager.telegram-secret-id" = module.telegram_secrets.secret_name
  })

  runtime_secret_arns = setunion(
    toset(values(var.backend_runtime_environment_secrets)),
    toset([module.runtime_secrets.secret_arn]),
    toset([
      module.database_secrets.secret_arn,
      module.whatsapp_secrets.secret_arn,
      module.telegram_secrets.secret_arn
    ]),
    var.shared_rds_secret_arn == null ? toset([]) : toset([var.shared_rds_secret_arn]),
    var.appconfig_secret_arns
  )
}

module "database_secrets" {
  source = "../../modules/runtime-secrets"

  name                = coalesce(var.database_secret_name, "wcs/${var.environment}/database")
  description         = "WCS database credentials. Replace the fake bootstrap JSON in Secrets Manager before enabling the runtime."
  initial_secret_json = local.fake_database_secret_json
  tags                = local.common_tags
}

module "whatsapp_secrets" {
  source = "../../modules/runtime-secrets"

  name                = coalesce(var.whatsapp_secret_name, "wcs/${var.environment}/whatsapp")
  description         = "WCS WhatsApp credentials. Replace the fake bootstrap JSON in Secrets Manager before enabling Meta."
  initial_secret_json = local.fake_whatsapp_secret_json
  tags                = local.common_tags
}

module "telegram_secrets" {
  source = "../../modules/runtime-secrets"

  name                = coalesce(var.telegram_secret_name, "wcs/${var.environment}/telegram")
  description         = "WCS Telegram credentials. Replace the fake bootstrap JSON in Secrets Manager before enabling Telegram."
  initial_secret_json = local.fake_telegram_secret_json
  tags                = local.common_tags
}

module "appconfig" {
  source = "../../modules/appconfig"

  application_name         = var.project_name
  environment_name         = var.environment
  profile_name             = "runtime"
  deployment_strategy_name = "${var.project_name}-${var.environment}-all-at-once"
  configuration_content    = coalesce(var.appconfig_configuration_content, local.fake_appconfig_configuration)
  tags                     = local.common_tags
}

module "wcs_knowledge_base" {
  source = "../../modules/bedrock-knowledge-base"

  project_name               = var.project_name
  environment                = var.environment
  aws_region                 = var.aws_region
  source_documents_directory = "${path.root}/../../../knowledge-base/wcs"
  tags                       = local.common_tags
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
  runtime_environment_variables = var.backend_runtime_environment_variables
  runtime_environment_secrets   = var.backend_runtime_environment_secrets
  runtime_secret_arns           = local.runtime_secret_arns
  vpc_connector_arn             = var.backend_vpc_connector_arn
  enable_bedrock_access         = var.enable_bedrock_access
  bedrock_model_arns            = var.bedrock_model_arns
  bedrock_knowledge_base_arns   = var.enable_bedrock_access ? [module.wcs_knowledge_base.knowledge_base_arn] : []
  tags                          = local.common_tags
}

module "github_backend_deploy" {
  count  = var.existing_github_oidc_provider_arn == null ? 0 : 1
  source = "../../modules/github-backend-deploy"

  project_name                  = var.project_name
  environment                   = var.environment
  github_repository             = var.github_repository
  github_repository_owner_id    = var.github_repository_owner_id
  github_repository_id          = var.github_repository_id
  github_oidc_provider_arn      = var.existing_github_oidc_provider_arn
  ecr_repository_arn            = module.backend_apprunner.ecr_repository_arn
  apprunner_service_arn         = module.backend_apprunner.apprunner_service_arn
  apprunner_ecr_access_role_arn = module.backend_apprunner.apprunner_ecr_access_role_arn
  tags                          = local.common_tags
}

module "github_terraform_deploy" {
  count  = var.existing_github_oidc_provider_arn == null ? 0 : 1
  source = "../../modules/github-terraform-deploy"

  project_name               = var.project_name
  environment                = var.environment
  github_repository          = var.github_repository
  github_repository_owner_id = var.github_repository_owner_id
  github_repository_id       = var.github_repository_id
  github_oidc_provider_arn   = var.existing_github_oidc_provider_arn
  aws_region                 = var.aws_region
  state_bucket_name          = "tesis-dev-terraform-state-us-east-1"
  state_key                  = "wally-customer-support/environments/prod/terraform.tfstate"
  secret_name_prefix         = "wcs/${var.environment}/"
  shared_rds_secret_arn      = var.shared_rds_secret_arn
  permissions_boundary_arn   = var.terraform_permissions_boundary_arn
  tags                       = local.common_tags
}
