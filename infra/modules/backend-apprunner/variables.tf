variable "project_name" {
  type        = string
  description = "Project name used for App Runner role names."
}

variable "environment" {
  type        = string
  description = "Deployment environment."
}

variable "ecr_repository_name" {
  type        = string
  description = "ECR repository name."
}

variable "create_service" {
  type        = bool
  description = "Whether to create the App Runner service."
  default     = false
}

variable "image_tag" {
  type        = string
  description = "Image tag selected when App Runner is created."
  default     = "bootstrap"
}

variable "cpu" {
  type        = string
  description = "App Runner CPU size."
  default     = "0.5 vCPU"
}

variable "memory" {
  type        = string
  description = "App Runner memory size."
  default     = "1 GB"
}

variable "container_port" {
  type        = number
  description = "Container HTTP port."
  default     = 8080
}

variable "health_check_path" {
  type        = string
  description = "HTTP health endpoint."
  default     = "/actuator/health"
}

variable "max_concurrency" {
  type        = number
  description = "Maximum concurrent requests per App Runner instance."
  default     = 80
}

variable "max_size" {
  type        = number
  description = "Maximum App Runner instances."
  default     = 2
}

variable "min_size" {
  type        = number
  description = "Minimum App Runner instances."
  default     = 1
}

variable "runtime_environment_variables" {
  type        = map(string)
  description = "Non-secret App Runner runtime variables."
  default     = {}
}

variable "runtime_environment_secrets" {
  type        = map(string)
  description = "App Runner runtime variables mapped to Secrets Manager ARNs."
  default     = {}
}

variable "runtime_secret_arns" {
  type        = set(string)
  description = "Secrets that the runtime IAM role may read."
  default     = []
}

variable "vpc_connector_arn" {
  type        = string
  description = "Existing App Runner VPC connector ARN, required only when egress_type is VPC."
  default     = null
}

variable "egress_type" {
  type        = string
  description = "App Runner outbound network mode. DEFAULT uses public App Runner egress; VPC requires a connector with internet or AWS endpoint routing."
  default     = "DEFAULT"

  validation {
    condition     = contains(["DEFAULT", "VPC"], var.egress_type)
    error_message = "egress_type must be DEFAULT or VPC."
  }
}

variable "enable_bedrock_access" {
  type        = bool
  description = "Whether to grant Bedrock invoke permissions."
  default     = false
}

variable "bedrock_model_arns" {
  type        = set(string)
  description = "Bedrock model ARNs allowed by the runtime role."
  default     = []
}

variable "bedrock_knowledge_base_arns" {
  type        = set(string)
  description = "Bedrock Knowledge Base ARNs allowed by the runtime role for Retrieve calls."
  default     = []
}

variable "enable_appconfig_access" {
  type        = bool
  description = "Whether to grant runtime AppConfig data-plane permissions."
  default     = true
}

variable "ecr_images_to_keep" {
  type        = number
  description = "Number of ECR images retained by lifecycle policy."
  default     = 20
}

variable "tags" {
  type        = map(string)
  description = "Tags applied to resources."
  default     = {}
}
