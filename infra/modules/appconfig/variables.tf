variable "application_name" {
  type        = string
  description = "AWS AppConfig application name."
}

variable "environment_name" {
  type        = string
  description = "AWS AppConfig environment name."
}

variable "profile_name" {
  type        = string
  description = "AWS AppConfig configuration profile name."
}

variable "deployment_strategy_name" {
  type        = string
  description = "Stable AWS AppConfig deployment strategy name."
  default     = null
}

variable "configuration_content" {
  type        = string
  nullable    = true
  description = "Optional JSON configuration. It may contain secret references, never secret values."
  default     = null
}

variable "feature_flags_profile_name" {
  type        = string
  description = "Dedicated hosted profile for non-secret business feature flags."
  default     = "feature-flags"
}

variable "feature_flags_configuration_content" {
  type        = string
  nullable    = true
  description = "Initial non-secret feature-flag document. Runtime publications are preserved outside Terraform."
  default     = null
}

variable "tags" {
  type        = map(string)
  description = "Tags applied to AppConfig resources."
  default     = {}
}
