output "aws_region" {
  value = var.aws_region
}

output "account_id" {
  value = data.aws_caller_identity.current.account_id
}

output "database_schema" {
  value = var.database_schema
}

output "shared_rds_endpoint" {
  description = "Existing RDS endpoint, when shared_rds_instance_identifier is configured."
  value       = try(data.aws_db_instance.shared[0].address, null)
}

output "shared_rds_port" {
  value = try(data.aws_db_instance.shared[0].port, null)
}

output "shared_rds_database_name" {
  value = try(data.aws_db_instance.shared[0].db_name, null)
}

output "shared_rds_secret_arn" {
  description = "Existing RDS secret reference; no secret value is exposed."
  value       = try(data.aws_secretsmanager_secret.shared_rds[0].arn, null)
}

output "appconfig_application_id" {
  value = module.appconfig.application_id
}

output "appconfig_environment_id" {
  value = module.appconfig.environment_id
}

output "appconfig_configuration_profile_id" {
  value = module.appconfig.configuration_profile_id
}

output "knowledge_base_source_bucket_name" {
  value = module.wcs_knowledge_base.source_bucket_name
}

output "knowledge_base_vector_bucket_name" {
  value = module.wcs_knowledge_base.vector_bucket_name
}

output "knowledge_base_vector_index_arn" {
  value = module.wcs_knowledge_base.vector_index_arn
}

output "knowledge_base_id" {
  value = module.wcs_knowledge_base.knowledge_base_id
}

output "knowledge_base_arn" {
  value = module.wcs_knowledge_base.knowledge_base_arn
}

output "knowledge_base_data_source_id" {
  value = module.wcs_knowledge_base.data_source_id
}

output "knowledge_base_service_role_arn" {
  value = module.wcs_knowledge_base.service_role_arn
}

output "runtime_secret_arn" {
  description = "WCS Secrets Manager container ARN; values are not managed by Terraform."
  value       = module.runtime_secrets.secret_arn
}

output "database_secret_arn" {
  description = "WCS database secret ARN; replace its fake value before enabling the runtime."
  value       = module.database_secrets.secret_arn
}

output "whatsapp_secret_arn" {
  description = "WCS WhatsApp secret ARN; replace its fake value before enabling Meta."
  value       = module.whatsapp_secrets.secret_arn
}

output "telegram_secret_arn" {
  description = "WCS Telegram secret ARN; replace its fake value before enabling Telegram."
  value       = module.telegram_secrets.secret_arn
}

output "backend_ecr_repository_name" {
  value = module.backend_apprunner.ecr_repository_name
}

output "backend_ecr_repository_url" {
  value = module.backend_apprunner.ecr_repository_url
}

output "backend_apprunner_service_arn" {
  value = module.backend_apprunner.apprunner_service_arn
}

output "backend_apprunner_service_url" {
  value = module.backend_apprunner.apprunner_service_url
}

output "backend_apprunner_ecr_access_role_arn" {
  value = module.backend_apprunner.apprunner_ecr_access_role_arn
}

output "backend_github_deploy_role_arn" {
  value = try(module.github_backend_deploy[0].role_arn, null)
}

output "terraform_github_deploy_role_arn" {
  description = "WCS Terraform CI role ARN for AWS_TERRAFORM_ROLE_ARN."
  value       = try(module.github_terraform_deploy[0].role_arn, null)
}
