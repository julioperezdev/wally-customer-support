variable "project_name" {
  type        = string
  description = "Project name used for resource names and tags."
  default     = "wally-customer-support"
}

variable "environment" {
  type        = string
  description = "Deployment environment."
  default     = "prod"
}

variable "aws_region" {
  type        = string
  description = "AWS region for regional resources. Confirm against the shared RDS before applying."
  default     = "us-east-1"
}

variable "github_repository" {
  type        = string
  description = "GitHub repository allowed to assume the backend deploy role."
  default     = "julioperezdev/wally-customer-support"
}

variable "existing_github_oidc_provider_arn" {
  type        = string
  description = "Existing GitHub Actions OIDC provider ARN. This stack does not create a second provider."
  default     = null
}

variable "terraform_permissions_boundary_arn" {
  type        = string
  description = "Optional IAM permissions boundary applied to the WCS Terraform CI role."
  default     = null
}

variable "shared_rds_instance_identifier" {
  type        = string
  description = "Identifier of the existing tesis-dev RDS instance. WCS reads it as a data source and never manages it."
  default     = null
}

variable "shared_rds_secret_arn" {
  type        = string
  description = "ARN of the existing RDS Secrets Manager secret. This is a reference, not a secret value."
  default     = null
}

variable "database_schema" {
  type        = string
  description = "Dedicated PostgreSQL schema owned by WCS migrations."
  default     = "wcs"

  validation {
    condition     = var.database_schema == "wcs"
    error_message = "database_schema must remain wcs until schema-qualified migrations and entities are made configurable."
  }
}

variable "runtime_secret_name" {
  type        = string
  description = "Secrets Manager container name. Terraform creates metadata only; secret values are managed separately."
  default     = null
}

variable "database_secret_name" {
  type        = string
  description = "WCS bootstrap database secret name. To use shared RDS, update the AppConfig reference and IAM ARN instead of renaming this secret."
  default     = null
}

variable "whatsapp_secret_name" {
  type        = string
  description = "WCS WhatsApp secret name."
  default     = null
}

variable "appconfig_configuration_content" {
  type        = string
  nullable    = true
  description = "Optional JSON AppConfig document containing non-secret settings and Secrets Manager references."
  default     = null
}

variable "backend_create_service" {
  type        = bool
  description = "Whether to create App Runner. Keep false until image, network and runtime configuration are verified."
  default     = false
}

variable "backend_image_tag" {
  type        = string
  description = "Immutable ECR tag used when App Runner is created."
  default     = "bootstrap"
}

variable "backend_cpu" {
  type        = string
  description = "App Runner CPU size."
  default     = "0.5 vCPU"
}

variable "backend_memory" {
  type        = string
  description = "App Runner memory size."
  default     = "1 GB"
}

variable "backend_vpc_connector_arn" {
  type        = string
  description = "Existing App Runner VPC connector ARN with network access to the shared RDS."
  default     = null
}

variable "backend_runtime_environment_variables" {
  type        = map(string)
  description = "Additional non-secret runtime variables. Values must not contain credentials."
  default     = {}
}

variable "backend_runtime_environment_secrets" {
  type        = map(string)
  description = "Optional App Runner secret mappings. Values are Secrets Manager ARNs, never secret values."
  default     = {}
}

variable "appconfig_secret_arns" {
  type        = set(string)
  description = "Secret ARNs referenced by AppConfig and readable by the runtime, never secret values."
  default     = []
}

variable "enable_bedrock_access" {
  type        = bool
  description = "Whether the App Runner runtime role may invoke the explicitly listed Bedrock models."
  default     = false
}

variable "bedrock_model_arns" {
  type        = set(string)
  description = "Bedrock model ARNs allowed to the runtime when enable_bedrock_access is true."
  default     = []
}
