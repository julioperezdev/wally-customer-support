data "aws_caller_identity" "current" {}

data "aws_appconfig_application" "wcs" {
  name = var.appconfig_application_name
}

data "aws_appconfig_configuration_profiles" "wcs" {
  application_id = data.aws_appconfig_application.wcs.id
}

data "aws_appconfig_configuration_profile" "all" {
  for_each = data.aws_appconfig_configuration_profiles.wcs.configuration_profile_ids

  application_id           = data.aws_appconfig_application.wcs.id
  configuration_profile_id = each.value
}

locals {
  common_tags = {
    Project     = var.project_name
    Environment = var.environment
    ManagedBy   = "terraform"
  }

  runtime_profile_ids = [
    for profile in data.aws_appconfig_configuration_profile.all : profile.configuration_profile_id
    if profile.name == var.appconfig_profile_name
  ]

  fake_database_secret_json = jsonencode({
    jdbc_url = "jdbc:postgresql://REPLACE_ME:5432/wcs_test"
    username = "REPLACE_ME_TEST_DATABASE_USERNAME"
    password = "REPLACE_ME_TEST_DATABASE_PASSWORD"
  })

  fake_whatsapp_secret_json = jsonencode({
    "access-token" = "REPLACE_ME_TEST_WHATSAPP_ACCESS_TOKEN"
    "verify-token" = "REPLACE_ME_TEST_WHATSAPP_VERIFY_TOKEN"
    "app-secret"   = "REPLACE_ME_TEST_META_APP_SECRET"
  })

  fake_telegram_secret_json = jsonencode({
    "bot-token"            = "REPLACE_ME_TEST_TELEGRAM_BOT_TOKEN"
    "webhook-secret-token" = "REPLACE_ME_TEST_TELEGRAM_WEBHOOK_SECRET"
  })

  fake_observability_secret_json = jsonencode({
    "actor-key-secret" = "REPLACE_ME_TEST_OBSERVABILITY_ACTOR_KEY_SECRET"
  })

  fake_mercado_pago_secret_json = jsonencode({
    "access-token"   = "REPLACE_ME_TEST_MERCADO_PAGO_ACCESS_TOKEN"
    "webhook-secret" = "REPLACE_ME_TEST_MERCADO_PAGO_WEBHOOK_SECRET"
  })

  test_configuration = {
    "wcs.ai.provider"                                             = "mock"
    "wcs.ai.model"                                                = "llm.mock.v1"
    "wcs.ai.request-timeout"                                      = "PT5S"
    "wcs.ai.prompt.intent-version"                                = "conversation-intent-v2"
    "wcs.ai.prompt.intent-max-output-tokens"                      = 1024
    "wcs.ai.prompt.intent-temperature"                            = 0.0
    "wcs.ai.prompt.max-input-characters"                          = 2000
    "wcs.ai.prompt.max-history-messages"                          = 12
    "wcs.ai.response.prompt-version"                              = "conversation-response-v1"
    "wcs.ai.response.max-output-tokens"                           = 256
    "wcs.ai.response.temperature"                                 = 0.0
    "wcs.ai.response.max-input-characters"                        = 2000
    "wcs.ai.response.max-history-messages"                        = 6
    "wcs.ai.response.max-knowledge-characters"                    = 8000
    "wcs.ai.response.max-summary-characters"                      = 4000
    "wcs.conversation.guardrails.min-intent-confidence"           = 0.65
    "wcs.rag.provider"                                            = "mock"
    "wcs.rag.max-results"                                         = 5
    "wcs.outbox.max-attempts"                                     = 3
    "wcs.payment.provider"                                        = "mock"
    "wcs.payment.currency"                                        = "ARS"
    "wcs.payment.mercado-pago.base-url"                           = "https://api.mercadopago.com"
    "wcs.payment.notification-url"                                = ""
    "wcs.payment.webhook.signature-required"                      = false
    "wcs.conversation.retention.enabled"                          = false
    "wcs.conversation.retention.content-retention"                = "PT720H"
    "wcs.conversation.retention.metadata-retention"               = "PT2160H"
    "wcs.conversation.retention.aggregate-metrics-retention"      = "PT8760H"
    "wcs.conversation.retention.cleanup-batch-size"               = 500
    "wcs.conversation.retention.schedule-delay-ms"                = 86400000
    "wcs.whatsapp.adapter"                                        = "mock"
    "wcs.telegram.enabled"                                        = false
    "wcs.telegram.adapter"                                        = "telegram"
    "wcs.external-config.appconfig.application"                   = var.appconfig_application_name
    "wcs.external-config.appconfig.environment"                   = var.environment
    "wcs.external-config.appconfig.profile"                       = var.appconfig_profile_name
    "wcs.external-config.appconfig.enabled"                       = true
    "wcs.external-config.secrets-manager.enabled"                 = true
    "wcs.external-config.secrets-manager.database-secret-id"      = module.database_secrets.secret_name
    "wcs.external-config.secrets-manager.whatsapp-secret-id"      = module.whatsapp_secrets.secret_name
    "wcs.external-config.secrets-manager.telegram-secret-id"      = module.telegram_secrets.secret_name
    "wcs.external-config.secrets-manager.observability-secret-id" = module.observability_secrets.secret_name
    "wcs.external-config.secrets-manager.mercado-pago-secret-id"  = module.mercado_pago_secrets.secret_name
    "wcs.agent-runtime.activation-enabled"                        = false
    "wcs.agent-runtime.environment"                               = var.environment
    "wcs.agent-runtime.shadow-enabled"                            = false
    "wcs.agent-runtime.shadow-provider"                           = "noop"
    "wcs.agent-runtime.shadow-allowed-environments"               = var.environment
    "wcs.agent-runtime.shadow-traffic-percentage"                 = 0
  }
}

resource "aws_appconfig_environment" "test" {
  application_id = data.aws_appconfig_application.wcs.id
  name           = var.environment
  description    = "Non-production WCS environment for controlled validation."
  tags           = local.common_tags
}

resource "aws_appconfig_deployment_strategy" "test" {
  name                           = "${var.project_name}-${var.environment}-all-at-once"
  deployment_duration_in_minutes = 0
  growth_factor                  = 100
  growth_type                    = "LINEAR"
  replicate_to                   = "NONE"
  description                    = "Controlled non-production WCS AppConfig deployment."
  tags                           = local.common_tags
}

resource "aws_appconfig_hosted_configuration_version" "test" {
  application_id           = data.aws_appconfig_application.wcs.id
  configuration_profile_id = one(local.runtime_profile_ids)
  content                  = jsonencode(local.test_configuration)
  content_type             = "application/json"
  description              = "Safe WCS test bootstrap; shadow remains disabled at 0%."

  lifecycle {
    ignore_changes = [content]
  }
}

resource "aws_appconfig_deployment" "test" {
  application_id           = data.aws_appconfig_application.wcs.id
  configuration_profile_id = one(local.runtime_profile_ids)
  configuration_version    = aws_appconfig_hosted_configuration_version.test.version_number
  deployment_strategy_id   = aws_appconfig_deployment_strategy.test.id
  environment_id           = aws_appconfig_environment.test.environment_id
  description              = "Deploy safe WCS test runtime configuration."

  lifecycle {
    ignore_changes = [configuration_version]
  }
}

module "database_secrets" {
  source = "../../modules/runtime-secrets"

  name                = "wcs/${var.environment}/database"
  description         = "WCS test database credentials. Replace bootstrap values before enabling a test runtime."
  initial_secret_json = local.fake_database_secret_json
  tags                = local.common_tags
}

module "whatsapp_secrets" {
  source = "../../modules/runtime-secrets"

  name                = "wcs/${var.environment}/whatsapp"
  description         = "WCS test WhatsApp credentials. Do not use production tokens."
  initial_secret_json = local.fake_whatsapp_secret_json
  tags                = local.common_tags
}

module "telegram_secrets" {
  source = "../../modules/runtime-secrets"

  name                = "wcs/${var.environment}/telegram"
  description         = "WCS test Telegram credentials. Do not use production tokens."
  initial_secret_json = local.fake_telegram_secret_json
  tags                = local.common_tags
}

module "observability_secrets" {
  source = "../../modules/runtime-secrets"

  name                = "wcs/${var.environment}/observability"
  description         = "WCS test pseudonymous observability key. Do not use production values."
  initial_secret_json = local.fake_observability_secret_json
  tags                = local.common_tags
}

module "mercado_pago_secrets" {
  source = "../../modules/runtime-secrets"

  name                = "wcs/${var.environment}/mercado-pago"
  description         = "WCS test Mercado Pago credentials. Do not use production tokens."
  initial_secret_json = local.fake_mercado_pago_secret_json
  tags                = local.common_tags
}

module "backend_apprunner" {
  source = "../../modules/backend-apprunner"

  project_name        = var.project_name
  environment         = var.environment
  ecr_repository_name = "${var.project_name}-${var.environment}-backend"
  create_service      = var.backend_create_service
  image_tag           = var.backend_image_tag
  runtime_environment_variables = {
    SPRING_PROFILES_ACTIVE = var.environment
    AWS_REGION             = var.aws_region
  }
  runtime_secret_arns = toset([
    module.database_secrets.secret_arn,
    module.whatsapp_secrets.secret_arn,
    module.telegram_secrets.secret_arn,
    module.observability_secrets.secret_arn,
    module.mercado_pago_secrets.secret_arn
  ])
  enable_appconfig_access = true
  enable_bedrock_access   = false
  tags                    = local.common_tags
}

module "github_terraform_deploy" {
  count  = var.existing_github_oidc_provider_arn == null ? 0 : 1
  source = "../../modules/github-terraform-deploy"

  project_name               = var.project_name
  environment                = var.environment
  github_repository          = var.github_repository
  github_environment         = var.environment
  github_repository_owner_id = var.github_repository_owner_id
  github_repository_id       = var.github_repository_id
  github_oidc_provider_arn   = var.existing_github_oidc_provider_arn
  aws_region                 = var.aws_region
  state_bucket_name          = "tesis-dev-terraform-state-us-east-1"
  state_key                  = "wally-customer-support/environments/test/terraform.tfstate"
  secret_name_prefix         = "wcs/${var.environment}/"
  permissions_boundary_arn   = var.terraform_permissions_boundary_arn
  tags                       = local.common_tags
}
