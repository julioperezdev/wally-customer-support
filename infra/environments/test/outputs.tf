output "aws_region" {
  value = var.aws_region
}

output "account_id" {
  value = data.aws_caller_identity.current.account_id
}

output "appconfig_application_id" {
  value = data.aws_appconfig_application.wcs.id
}

output "appconfig_environment_id" {
  value = aws_appconfig_environment.test.environment_id
}

output "appconfig_configuration_profile_id" {
  value = one(local.runtime_profile_ids)
}

output "database_secret_arn" {
  value = module.database_secrets.secret_arn
}

output "whatsapp_secret_arn" {
  value = module.whatsapp_secrets.secret_arn
}

output "telegram_secret_arn" {
  value = module.telegram_secrets.secret_arn
}

output "backend_ecr_repository_name" {
  value = module.backend_apprunner.ecr_repository_name
}

output "backend_apprunner_service_arn" {
  value = module.backend_apprunner.apprunner_service_arn
}

output "terraform_github_deploy_role_arn" {
  description = "Test Terraform CI role ARN for the GitHub test Environment."
  value       = try(module.github_terraform_deploy[0].role_arn, null)
}
