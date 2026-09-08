output "terraform_role_arn" {
  description = "ARN to configure as AWS_TERRAFORM_ROLE_ARN in GitHub Environment test."
  value       = module.github_terraform_deploy.role_arn
}

output "terraform_role_name" {
  description = "Name of the test Terraform OIDC role."
  value       = module.github_terraform_deploy.role_name
}

output "state_key" {
  description = "State key authorized for the test Terraform role."
  value       = var.state_key
}
