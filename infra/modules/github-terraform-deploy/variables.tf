variable "project_name" {
  type        = string
  description = "Project name used for resource names and IAM scope."
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

variable "aws_region" {
  type        = string
  description = "AWS region where WCS regional resources are managed."
}

variable "state_bucket_name" {
  type        = string
  description = "Existing S3 bucket that stores Terraform state."
}

variable "state_key" {
  type        = string
  description = "Exact Terraform state key managed by this role."
}

variable "secret_name_prefix" {
  type        = string
  description = "Secrets Manager name prefix managed by the WCS stack."
}

variable "shared_rds_secret_arn" {
  type        = string
  description = "Optional existing RDS secret whose metadata WCS reads."
  default     = null
}

variable "permissions_boundary_arn" {
  type        = string
  description = "Optional permissions boundary for the Terraform role."
  default     = null
}

variable "tags" {
  type        = map(string)
  description = "Tags applied to the Terraform role."
  default     = {}
}
