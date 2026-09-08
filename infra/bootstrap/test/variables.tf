variable "project_name" {
  type        = string
  description = "Stable WCS project name used for the test Terraform role."
  default     = "wally-customer-support"
}

variable "environment" {
  type        = string
  description = "Environment managed by this bootstrap root."
  default     = "test"

  validation {
    condition     = var.environment == "test"
    error_message = "This bootstrap root can only create the test Terraform role."
  }
}

variable "aws_region" {
  type        = string
  description = "AWS region where WCS regional resources are managed."
  default     = "us-east-1"
}

variable "github_repository" {
  type        = string
  description = "GitHub repository in owner/name form."
  default     = "julioperezdev/wally-customer-support"
}

variable "github_repository_owner_id" {
  type        = string
  description = "Immutable numeric GitHub owner ID used by the OIDC subject claim."
}

variable "github_repository_id" {
  type        = string
  description = "Immutable numeric GitHub repository ID used by the OIDC subject claim."
}

variable "github_oidc_provider_arn" {
  type        = string
  description = "Existing GitHub Actions OIDC provider ARN in the target AWS account."
}

variable "state_bucket_name" {
  type        = string
  description = "Existing S3 bucket used by the test Terraform state."
  default     = "tesis-dev-terraform-state-us-east-1"
}

variable "state_key" {
  type        = string
  description = "Exact state key granted to the test Terraform role."
  default     = "wally-customer-support/environments/test/terraform.tfstate"
}

variable "secret_name_prefix" {
  type        = string
  description = "Secrets Manager prefix granted to the test Terraform role."
  default     = "wcs/test/"
}

variable "permissions_boundary_arn" {
  type        = string
  description = "Optional permissions boundary for the test Terraform role."
  default     = null
  nullable    = true
}

variable "tags" {
  type        = map(string)
  description = "Tags applied to the bootstrap-created role and policy."
  default = {
    Project     = "wally-customer-support"
    Environment = "test"
    ManagedBy   = "terraform-bootstrap"
  }
}
