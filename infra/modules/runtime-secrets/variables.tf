variable "name" {
  type        = string
  description = "Secrets Manager secret name."
}

variable "description" {
  type        = string
  description = "Secret metadata description."
}

variable "initial_secret_json" {
  type        = string
  nullable    = true
  sensitive   = true
  description = "Optional initial JSON value. Use only clearly fake bootstrap values; console rotations are preserved."
  default     = null
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
