variable "project_name" {
  type        = string
  description = "Project name used for role names."
}

variable "environment" {
  type        = string
  description = "Deployment environment."
}

variable "github_repository" {
  type        = string
  description = "GitHub repository in owner/name form."
}

variable "github_repository_owner_id" {
  type        = string
  description = "Immutable numeric GitHub owner ID used by the repository OIDC subject claim."
  default     = null
}

variable "github_repository_id" {
  type        = string
  description = "Immutable numeric GitHub repository ID used by the repository OIDC subject claim."
  default     = null
}

variable "github_oidc_provider_arn" {
  type        = string
  description = "Existing GitHub Actions OIDC provider ARN."
}

variable "ecr_repository_arn" {
  type        = string
  description = "ECR repository ARN allowed for image publishing."
}

variable "apprunner_service_arn" {
  type        = string
  description = "Optional App Runner service ARN."
  default     = null
}

variable "apprunner_ecr_access_role_arn" {
  type        = string
  description = "Optional App Runner ECR access role ARN for iam:PassRole."
  default     = null
}

variable "tags" {
  type        = map(string)
  description = "Tags applied to the deploy role."
  default     = {}
}
