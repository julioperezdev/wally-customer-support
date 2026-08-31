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

variable "tags" {
  type        = map(string)
  description = "Tags applied to AppConfig resources."
  default     = {}
}
