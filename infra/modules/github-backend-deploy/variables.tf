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
