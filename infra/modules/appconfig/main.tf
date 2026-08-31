resource "aws_appconfig_application" "this" {
  name        = var.application_name
  description = "Non-secret runtime configuration for Wally Customer Support."
  tags        = var.tags
}

resource "aws_appconfig_environment" "this" {
  application_id = aws_appconfig_application.this.id
  name           = var.environment_name
  description    = "Runtime environment for Wally Customer Support."
  tags           = var.tags
}

resource "aws_appconfig_configuration_profile" "this" {
  application_id = aws_appconfig_application.this.id
  name           = var.profile_name
  location_uri   = "hosted"
  type           = "AWS.Freeform"
  description    = "Versioned non-secret WCS runtime settings."
  tags           = var.tags
}

resource "aws_appconfig_hosted_configuration_version" "this" {
  count = var.configuration_content == null ? 0 : 1

  application_id           = aws_appconfig_application.this.id
  configuration_profile_id = aws_appconfig_configuration_profile.this.id
  content                  = var.configuration_content
  content_type             = "application/json"
  description              = "Versioned WCS runtime configuration managed by Terraform."
}

resource "aws_appconfig_deployment_strategy" "this" {
  count = var.configuration_content == null ? 0 : 1

  name                           = "${var.application_name}-all-at-once"
  deployment_duration_in_minutes = 0
  growth_factor                  = 100
  growth_type                    = "LINEAR"
  replicate_to                   = "NONE"
  description                    = "Controlled deployment strategy for WCS AppConfig configuration."
  tags                           = var.tags
}

resource "aws_appconfig_deployment" "this" {
  count = var.configuration_content == null ? 0 : 1

  application_id           = aws_appconfig_application.this.id
  configuration_profile_id = aws_appconfig_configuration_profile.this.id
  configuration_version    = aws_appconfig_hosted_configuration_version.this[0].version_number
  deployment_strategy_id   = aws_appconfig_deployment_strategy.this[0].id
  environment_id           = aws_appconfig_environment.this.environment_id
  description              = "Deploy WCS runtime configuration."
}
