variable "project_name" {
  type        = string
  description = "Stable project name used in Cognito resource names."
}

variable "environment" {
  type        = string
  description = "WCS environment."
}

variable "aws_region" {
  type        = string
  description = "AWS region where the User Pool is created."
}

variable "domain_prefix" {
  type        = string
  description = "Globally unique Cognito hosted UI domain prefix. Set null to provision no hosted UI domain."
  default     = null
  nullable    = true

  validation {
    condition     = var.domain_prefix == null || can(regex("^[a-z0-9-]{1,63}$", var.domain_prefix))
    error_message = "domain_prefix must contain only lowercase letters, numbers and hyphens, with at most 63 characters."
  }
}

variable "callback_urls" {
  type        = list(string)
  description = "Allowed OAuth callback URLs for the public backoffice client."
  default     = ["http://localhost:5173/auth/callback"]
}

variable "logout_urls" {
  type        = list(string)
  description = "Allowed OAuth logout URLs for the public backoffice client."
  default     = ["http://localhost:5173/"]
}

variable "enable_password_auth" {
  type        = bool
  description = "Allow USER_PASSWORD_AUTH for controlled CLI smoke tests. PKCE remains the frontend flow."
  default     = false
}

variable "deletion_protection" {
  type        = string
  description = "Cognito User Pool deletion protection mode."
  default     = "ACTIVE"

  validation {
    condition     = contains(["ACTIVE", "INACTIVE"], var.deletion_protection)
    error_message = "deletion_protection must be ACTIVE or INACTIVE."
  }
}

variable "tags" {
  type        = map(string)
  description = "Tags applied to Cognito resources."
  default     = {}
}
