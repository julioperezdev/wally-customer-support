data "aws_caller_identity" "current" {}

data "aws_partition" "current" {}

locals {
  account_id = data.aws_caller_identity.current.account_id
  partition  = data.aws_partition.current.partition

  source_bucket_name = coalesce(
    var.source_bucket_name,
    "${var.project_name}-${var.environment}-kb-source-${local.account_id}"
  )
  vector_bucket_name = coalesce(
    var.vector_bucket_name,
    "${var.project_name}-${var.environment}-kb-vectors-${local.account_id}"
  )
  knowledge_base_name = coalesce(
    var.knowledge_base_name,
    "${var.project_name}-${var.environment}-knowledge-base"
  )
  embedding_model_arn = "arn:${local.partition}:bedrock:${var.aws_region}::foundation-model/${var.embedding_model_id}"
}

resource "aws_s3_bucket" "source" {
  bucket        = local.source_bucket_name
  force_destroy = false
  tags          = var.tags
}

resource "aws_s3_bucket_ownership_controls" "source" {
  bucket = aws_s3_bucket.source.id

  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_public_access_block" "source" {
  bucket = aws_s3_bucket.source.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "source" {
  bucket = aws_s3_bucket.source.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_versioning" "source" {
  bucket = aws_s3_bucket.source.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "source" {
  bucket = aws_s3_bucket.source.id

  rule {
    id     = "retain-document-history"
    status = "Enabled"

    noncurrent_version_expiration {
      noncurrent_days = 90
    }
  }
}

resource "aws_s3_object" "source_documents" {
  for_each = fileset(var.source_documents_directory, "*.md")

  bucket       = aws_s3_bucket.source.id
  key          = "documents/${each.value}"
  source       = "${var.source_documents_directory}/${each.value}"
  content_type = "text/markdown; charset=utf-8"
  etag         = filemd5("${var.source_documents_directory}/${each.value}")
}

resource "aws_s3vectors_vector_bucket" "this" {
  vector_bucket_name = local.vector_bucket_name
  tags               = var.tags
}

resource "aws_s3vectors_index" "this" {
  index_name         = var.vector_index_name
  vector_bucket_name = aws_s3vectors_vector_bucket.this.vector_bucket_name
  data_type          = "float32"
  dimension          = var.embedding_dimensions
  distance_metric    = "euclidean"
  tags               = var.tags

  metadata_configuration {
    non_filterable_metadata_keys = [
      "AMAZON_BEDROCK_TEXT",
      "AMAZON_BEDROCK_METADATA",
    ]
  }
}

data "aws_iam_policy_document" "knowledge_base_assume_role" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["bedrock.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "aws:SourceAccount"
      values   = [local.account_id]
    }
  }
}

resource "aws_iam_role" "knowledge_base" {
  name               = "${var.project_name}-${var.environment}-bedrock-kb"
  assume_role_policy = data.aws_iam_policy_document.knowledge_base_assume_role.json
  tags               = var.tags
}

data "aws_iam_policy_document" "knowledge_base" {
  statement {
    sid       = "ListSourceDocuments"
    effect    = "Allow"
    actions   = ["s3:ListBucket"]
    resources = [aws_s3_bucket.source.arn]

    condition {
      test     = "StringLike"
      variable = "s3:prefix"
      values   = ["documents", "documents/*"]
    }
  }

  statement {
    sid       = "ReadSourceDocuments"
    effect    = "Allow"
    actions   = ["s3:GetObject"]
    resources = ["${aws_s3_bucket.source.arn}/documents/*"]
  }

  statement {
    sid       = "InvokeEmbeddingModel"
    effect    = "Allow"
    actions   = ["bedrock:InvokeModel"]
    resources = [local.embedding_model_arn]
  }

  statement {
    sid    = "UseVectorIndex"
    effect = "Allow"
    actions = [
      "s3vectors:DeleteVectors",
      "s3vectors:GetIndex",
      "s3vectors:GetVectors",
      "s3vectors:PutVectors",
      "s3vectors:QueryVectors",
    ]
    resources = [aws_s3vectors_index.this.index_arn]
  }
}

resource "aws_iam_role_policy" "knowledge_base" {
  name   = "knowledge-base-access"
  role   = aws_iam_role.knowledge_base.id
  policy = data.aws_iam_policy_document.knowledge_base.json
}

data "aws_iam_policy_document" "vector_bucket" {
  statement {
    sid    = "BedrockKnowledgeBaseVectorAccess"
    effect = "Allow"

    principals {
      type        = "AWS"
      identifiers = [aws_iam_role.knowledge_base.arn]
    }

    actions = [
      "s3vectors:DeleteVectors",
      "s3vectors:GetIndex",
      "s3vectors:GetVectors",
      "s3vectors:PutVectors",
      "s3vectors:QueryVectors",
    ]
    resources = [aws_s3vectors_index.this.index_arn]
  }
}

resource "aws_s3vectors_vector_bucket_policy" "this" {
  vector_bucket_arn = aws_s3vectors_vector_bucket.this.vector_bucket_arn
  policy            = data.aws_iam_policy_document.vector_bucket.json
}

resource "aws_bedrockagent_knowledge_base" "this" {
  name        = local.knowledge_base_name
  description = "WCS static customer-support knowledge. Dynamic catalog data stays in PostgreSQL tools."
  role_arn    = aws_iam_role.knowledge_base.arn
  tags        = var.tags

  knowledge_base_configuration {
    type = "VECTOR"

    vector_knowledge_base_configuration {
      embedding_model_arn = local.embedding_model_arn

      embedding_model_configuration {
        bedrock_embedding_model_configuration {
          dimensions          = var.embedding_dimensions
          embedding_data_type = var.embedding_data_type
        }
      }
    }
  }

  storage_configuration {
    type = "S3_VECTORS"

    s3_vectors_configuration {
      index_arn = aws_s3vectors_index.this.index_arn
    }
  }

  depends_on = [aws_s3vectors_vector_bucket_policy.this]
}

resource "aws_bedrockagent_data_source" "this" {
  knowledge_base_id    = aws_bedrockagent_knowledge_base.this.id
  name                 = "wcs-markdown-source"
  description          = "Versioned WCS Markdown documents from the repository."
  data_deletion_policy = "RETAIN"

  data_source_configuration {
    type = "S3"

    s3_configuration {
      bucket_arn        = aws_s3_bucket.source.arn
      inclusion_prefixes = ["documents/"]
    }
  }
}
