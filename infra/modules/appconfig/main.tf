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
