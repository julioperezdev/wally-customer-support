output "source_bucket_name" {
  value = aws_s3_bucket.source.bucket
}

output "source_bucket_arn" {
  value = aws_s3_bucket.source.arn
}

output "vector_bucket_name" {
  value = aws_s3vectors_vector_bucket.this.vector_bucket_name
}

output "vector_bucket_arn" {
  value = aws_s3vectors_vector_bucket.this.vector_bucket_arn
}

output "vector_index_name" {
  value = aws_s3vectors_index.this.index_name
}

output "vector_index_arn" {
  value = aws_s3vectors_index.this.index_arn
}

output "knowledge_base_id" {
  value = aws_bedrockagent_knowledge_base.this.id
}

output "knowledge_base_arn" {
  value = aws_bedrockagent_knowledge_base.this.arn
}

output "data_source_id" {
  value = aws_bedrockagent_data_source.this.data_source_id
}

output "service_role_arn" {
  value = aws_iam_role.knowledge_base.arn
}
