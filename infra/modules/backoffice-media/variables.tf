variable "project_name" {
  type        = string
  description = "Project name used in the media bucket name."
}

variable "environment" {
  type        = string
  description = "Environment owning the media bucket."
}

variable "bucket_name" {
  type        = string
  description = "Optional globally unique S3 bucket name."
  default     = null
  nullable    = true
}

variable "cors_allowed_origins" {
  type        = list(string)
  description = "Browser origins allowed to use the presigned upload URL."
  default     = ["http://localhost:5173"]
}

variable "tags" {
  type        = map(string)
  description = "Tags applied to media resources."
  default     = {}
}
