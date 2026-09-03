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

variable "tags" {
  type        = map(string)
  description = "Tags applied to AppConfig resources."
  default     = {}
}
