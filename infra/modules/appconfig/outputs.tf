output "application_id" {
  value = aws_appconfig_application.this.id
}

output "application_name" {
  value = aws_appconfig_application.this.name
}

output "environment_id" {
  value = aws_appconfig_environment.this.environment_id
}

output "configuration_profile_id" {
  value = aws_appconfig_configuration_profile.this.configuration_profile_id
}
