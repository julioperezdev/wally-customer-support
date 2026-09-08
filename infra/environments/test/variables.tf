variable "project_name" {
  type        = string
  description = "Stable WCS project name used by shared AppConfig and test resources."
  default     = "wally-customer-support"
}

variable "environment" {
  type        = string
  description = "Non-production deployment environment."
  default     = "test"

  validation {
    condition     = var.environment == "test"
    error_message = "The non-production WCS stack must remain test."
  }
}

variable "aws_region" {
  type        = string
  description = "AWS region for WCS test resources."
  default     = "us-east-1"
}

variable "appconfig_application_name" {
  type        = string
  description = "Existing stable AppConfig application created by the production stack."
  default     = "wally-customer-support"
}

variable "appconfig_profile_name" {
  type        = string
  description = "Existing AppConfig configuration profile shared by WCS environments."
  default     = "runtime"
}

variable "backend_create_service" {
  type        = bool
  description = "Whether to create the test App Runner service. Keep false until test secrets and database target are reviewed."
  default     = false
}

variable "backend_image_tag" {
  type        = string
  description = "Immutable image tag used if the test App Runner service is enabled."
  default     = "bootstrap"
}

variable "github_repository" {
  type        = string
  description = "Repository associated with WCS CI."
  default     = "julioperezdev/wally-customer-support"
}

variable "existing_github_oidc_provider_arn" {
  type        = string
  description = "Existing GitHub Actions OIDC provider ARN."
  default     = null
}

variable "github_repository_owner_id" {
  type        = string
  description = "Immutable numeric GitHub owner ID."
  default     = null
}

variable "github_repository_id" {
  type        = string
  description = "Immutable numeric GitHub repository ID."
  default     = null
}

variable "terraform_permissions_boundary_arn" {
  type        = string
  description = "Optional IAM permissions boundary for the test Terraform role."
  default     = null
}
