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

  fake_backoffice_secret_json = jsonencode({
    "preview-token" = "REPLACE_ME_BACKOFFICE_PREVIEW_TOKEN"
  })

  fake_observability_secret_json = jsonencode({
    "actor-key-secret" = "REPLACE_ME_OBSERVABILITY_ACTOR_KEY_SECRET"
  })

  fake_mercado_pago_secret_json = jsonencode({
    "access-token"   = "REPLACE_ME_MERCADO_PAGO_ACCESS_TOKEN"
    "webhook-secret" = "REPLACE_ME_MERCADO_PAGO_WEBHOOK_SECRET"
  })

  # This bootstrap document mirrors the currently deployed AppConfig baseline
  # non-secret baseline. Runtime changes made in AppConfig remain protected by
  # ignore_changes in the AppConfig module; this document is used when the
  # hosted configuration is created or explicitly overridden.
  default_appconfig_configuration = {
    "wcs.whatsapp.adapter"                                        = "meta"
    "wcs.whatsapp.graph-api-version"                              = "v25.0"
    "wcs.whatsapp.graph-api-base-url"                             = "https://graph.facebook.com"
    "wcs.whatsapp.phone-number-id"                                = "1271920986004478"
    "wcs.whatsapp.business-account-id"                            = "1684722242599448"
    "wcs.whatsapp.allowed-recipient"                              = "5491159230699"
    "wcs.telegram.enabled"                                        = true
    "wcs.telegram.adapter"                                        = "telegram"
    "wcs.telegram.api-base-url"                                   = "https://api.telegram.org"
    "wcs.telegram.allowed-chat-id"                                = ""
    "wcs.telegram.connect-timeout"                                = "PT2S"
    "wcs.telegram.read-timeout"                                   = "PT5S"
    "wcs.ai.provider"                                             = "bedrock"
    "wcs.ai.model"                                                = "openai.gpt-oss-20b-1:0"
    "wcs.ai.region"                                               = var.aws_region
    "wcs.ai.pricing-version"                                      = "aws-bedrock-us-east-1-standard-2026-09"
    "wcs.ai.input-price-usd-per-million-tokens"                   = 0.0721
    "wcs.ai.output-price-usd-per-million-tokens"                  = 0.3090
    "wcs.ai.request-timeout"                                      = "PT30S"
    "wcs.ai.prompt.provider"                                      = "classpath"
    "wcs.ai.prompt.intent-version"                                = "conversation-intent-v2"
    "wcs.ai.prompt.intent-max-output-tokens"                      = 1024
    "wcs.ai.prompt.intent-temperature"                            = 0.0
    "wcs.ai.prompt.max-input-characters"                          = 2000
    "wcs.ai.prompt.max-history-messages"                          = 12
    "wcs.ai.response.prompt-version"                              = "conversation-response-v1"
    "wcs.ai.response.max-output-tokens"                           = 1024
    "wcs.ai.response.temperature"                                 = 0.2
    "wcs.ai.response.max-input-characters"                        = 2000
    "wcs.ai.response.max-history-messages"                        = 6
    "wcs.ai.response.max-knowledge-characters"                    = 8000
    "wcs.ai.response.max-summary-characters"                      = 4000
    "wcs.conversation.guardrails.min-intent-confidence"           = 0.65
    "wcs.rag.provider"                                            = "bedrock-kb"
    "wcs.rag.max-results"                                         = 5
    "wcs.rag.knowledge-base-id"                                   = module.wcs_knowledge_base.knowledge_base_id
    "wcs.rag.region"                                              = var.aws_region
    "wcs.outbox.max-attempts"                                     = 3
    "wcs.payment.provider"                                        = "mock"
    "wcs.payment.currency"                                        = "ARS"
    "wcs.payment.mercado-pago.base-url"                           = "https://api.mercadopago.com"
    "wcs.payment.notification-url"                                = ""
    "wcs.payment.webhook.signature-required"                      = true
    "wcs.conversation.preferences.enabled"                        = false
    "wcs.conversation.preferences.ttl"                            = "PT24H"
    "wcs.conversation.preferences.max-preferences"                = 5
    "wcs.conversation.preferences.max-value-characters"           = 64
    "wcs.conversation.retention.enabled"                          = false
    "wcs.conversation.retention.content-retention"                = "PT720H"
    "wcs.conversation.retention.metadata-retention"               = "PT2160H"
    "wcs.conversation.retention.aggregate-metrics-retention"      = "PT8760H"
    "wcs.conversation.retention.cleanup-batch-size"               = 500
    "wcs.conversation.retention.schedule-delay-ms"                = 86400000
    "wcs.external-config.secrets-manager.database-secret-id"      = module.database_secrets.secret_name
    "wcs.external-config.secrets-manager.whatsapp-secret-id"      = module.whatsapp_secrets.secret_name
    "wcs.external-config.secrets-manager.telegram-secret-id"      = module.telegram_secrets.secret_name
    "wcs.external-config.secrets-manager.backoffice-secret-id"    = module.backoffice_secrets.secret_name
    "wcs.external-config.secrets-manager.observability-secret-id" = module.observability_secrets.secret_name
    "wcs.external-config.secrets-manager.mercado-pago-secret-id"  = module.mercado_pago_secrets.secret_name
    "wcs.backoffice.enabled"                                      = var.backoffice_preview_enabled
    "wcs.backoffice.preview.enabled"                              = var.backoffice_preview_enabled
  }

  default_feature_flags_configuration = {
    schemaVersion = "1"
    version       = "wcs-121-initial"
    flags = [
      {
        key           = "wcs.agent.catalog-specialist.enabled"
        enabled       = true
        killSwitch    = false
        environments  = [var.environment]
        channels      = []
        useCases      = []
        agentIds      = ["catalog-specialist"]
        agentVersions = []
      }
    ]
  }

  runtime_secret_arns = setunion(
    toset(values(var.backend_runtime_environment_secrets)),
    toset([module.runtime_secrets.secret_arn]),
    toset([
      module.database_secrets.secret_arn,
      module.whatsapp_secrets.secret_arn,
      module.telegram_secrets.secret_arn,
      module.backoffice_secrets.secret_arn,
      module.observability_secrets.secret_arn,
      module.mercado_pago_secrets.secret_arn
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
  configuration_content = jsonencode(merge(
    local.default_appconfig_configuration,
    var.appconfig_configuration_content == null ? {} : jsondecode(var.appconfig_configuration_content)
  ))
  feature_flags_profile_name          = "feature-flags"
  feature_flags_configuration_content = jsonencode(local.default_feature_flags_configuration)
  tags                                = local.common_tags
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

module "backoffice_secrets" {
  source = "../../modules/runtime-secrets"

  name                = coalesce(var.backoffice_secret_name, "wcs/${var.environment}/backoffice")
  description         = "WCS backoffice preview token. Replace the fake bootstrap JSON before enabling the preview."
  initial_secret_json = local.fake_backoffice_secret_json
  tags                = local.common_tags
}

module "observability_secrets" {
  source = "../../modules/runtime-secrets"

  name                = "wcs/${var.environment}/observability"
  description         = "WCS pseudonymous observability key. Replace the fake bootstrap value before using actor grouping."
  initial_secret_json = local.fake_observability_secret_json
  tags                = local.common_tags
}

module "mercado_pago_secrets" {
  source = "../../modules/runtime-secrets"

  name                = "wcs/${var.environment}/mercado-pago"
  description         = "WCS Mercado Pago credentials. Replace bootstrap values before enabling the provider."
  initial_secret_json = local.fake_mercado_pago_secret_json
  tags                = local.common_tags
}

module "backend_apprunner" {
  source = "../../modules/backend-apprunner"

  # App Runner validates iam:PassRole during CreateService. Ensure the
  # Terraform deploy role policy is updated before that API call is made.
  depends_on = [module.github_terraform_deploy]

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
  egress_type                   = var.backend_egress_type
  vpc_connector_arn             = var.backend_vpc_connector_arn
  enable_bedrock_access         = var.enable_bedrock_access
  enable_appconfig_management   = var.enable_appconfig_management
  bedrock_model_arns            = var.bedrock_model_arns
  bedrock_prompt_arns           = var.bedrock_prompt_arns
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
