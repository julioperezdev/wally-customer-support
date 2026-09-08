module "github_terraform_deploy" {
  source = "../../modules/github-terraform-deploy"

  project_name               = var.project_name
  environment                = var.environment
  github_repository          = var.github_repository
  github_environment         = "test"
  github_repository_owner_id = var.github_repository_owner_id
  github_repository_id       = var.github_repository_id
  github_oidc_provider_arn   = var.github_oidc_provider_arn
  aws_region                 = var.aws_region
  state_bucket_name          = var.state_bucket_name
  state_key                  = var.state_key
  secret_name_prefix         = var.secret_name_prefix
  permissions_boundary_arn   = var.permissions_boundary_arn
  tags                       = var.tags
}
