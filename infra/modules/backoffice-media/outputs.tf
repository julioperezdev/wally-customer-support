output "bucket_name" {
  value = aws_s3_bucket.this.bucket
}

output "bucket_arn" {
  value = aws_s3_bucket.this.arn
}

output "object_arn" {
  value = "${aws_s3_bucket.this.arn}/*"
}
