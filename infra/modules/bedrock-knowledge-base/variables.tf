variable "project_name" {
  type        = string
  description = "Project name used for WCS Knowledge Base resource names."
}

variable "environment" {
  type        = string
  description = "Deployment environment."
}

variable "aws_region" {
  type        = string
  description = "AWS region for the source bucket, vector store and Knowledge Base."
}

variable "knowledge_base_name" {
  type        = string
  description = "Optional stable name for the Bedrock Knowledge Base."
  default     = null
}

variable "source_bucket_name" {
  type        = string
  description = "Optional stable name for the S3 source bucket."
  default     = null
}

variable "vector_bucket_name" {
  type        = string
  description = "Optional stable name for the S3 Vectors bucket."
  default     = null
}

variable "vector_index_name" {
  type        = string
  description = "Stable S3 Vectors index name."
  default     = "wcs-knowledge-base-index"
}

variable "source_documents_directory" {
  type        = string
  description = "Absolute path to the versioned Markdown source documents."
}

variable "embedding_model_id" {
  type        = string
  description = "Bedrock embedding model ID."
  default     = "amazon.titan-embed-text-v2:0"
}

variable "embedding_dimensions" {
  type        = number
  description = "Embedding vector dimensions."
  default     = 1024
}

variable "embedding_data_type" {
  type        = string
  description = "Embedding vector data type supported by the S3 Vectors index."
  default     = "FLOAT32"

  validation {
    condition     = var.embedding_data_type == "FLOAT32"
    error_message = "WCS currently supports FLOAT32 embeddings only."
  }
}

variable "tags" {
  type        = map(string)
  description = "Tags applied to WCS Knowledge Base resources."
  default     = {}
}
