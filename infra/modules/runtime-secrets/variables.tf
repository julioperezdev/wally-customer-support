variable "name" {
  type        = string
  description = "Secrets Manager secret name."
}

variable "description" {
  type        = string
  description = "Secret metadata description."
}

variable "recovery_window_in_days" {
  type        = number
  description = "Recovery window used if the secret container is destroyed."
  default     = 30
}

variable "tags" {
  type        = map(string)
  description = "Tags applied to the secret container."
  default     = {}
}
