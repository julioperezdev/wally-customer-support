resource "aws_ecr_repository" "backend" {
  name                 = var.ecr_repository_name
  image_tag_mutability = "IMMUTABLE"
  tags                 = var.tags

  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_ecr_lifecycle_policy" "backend" {
  repository = aws_ecr_repository.backend.name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Keep the last ${var.ecr_images_to_keep} backend images"
        selection = {
          tagStatus   = "any"
          countType   = "imageCountMoreThan"
          countNumber = var.ecr_images_to_keep
        }
        action = {
          type = "expire"
        }
      }
    ]
  })
}

data "aws_iam_policy_document" "apprunner_ecr_assume_role" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["build.apprunner.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "apprunner_ecr_access" {
  name               = "${var.project_name}-${var.environment}-apprunner-ecr-access"
  assume_role_policy = data.aws_iam_policy_document.apprunner_ecr_assume_role.json
  tags               = var.tags
}

resource "aws_iam_role_policy_attachment" "apprunner_ecr_access" {
  role       = aws_iam_role.apprunner_ecr_access.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSAppRunnerServicePolicyForECRAccess"
}

data "aws_iam_policy_document" "apprunner_instance_assume_role" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["tasks.apprunner.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "apprunner_instance" {
  name               = "${var.project_name}-${var.environment}-apprunner-instance"
  assume_role_policy = data.aws_iam_policy_document.apprunner_instance_assume_role.json
  tags               = var.tags
}

data "aws_iam_policy_document" "apprunner_instance" {
  dynamic "statement" {
    for_each = length(var.runtime_secret_arns) == 0 ? [] : [1]

    content {
      sid       = "ReadRuntimeSecrets"
      effect    = "Allow"
      actions   = ["secretsmanager:GetSecretValue"]
      resources = sort(tolist(var.runtime_secret_arns))
    }
  }

  dynamic "statement" {
    for_each = var.enable_bedrock_access && length(var.bedrock_model_arns) > 0 ? [1] : []

    content {
      sid       = "InvokeApprovedBedrockModels"
      effect    = "Allow"
      actions   = ["bedrock:InvokeModel", "bedrock:InvokeModelWithResponseStream"]
      resources = sort(tolist(var.bedrock_model_arns))
    }
  }

  dynamic "statement" {
    for_each = var.enable_bedrock_access && length(var.bedrock_knowledge_base_arns) > 0 ? [1] : []

    content {
      sid       = "RetrieveApprovedKnowledgeBases"
      effect    = "Allow"
      actions   = ["bedrock:Retrieve"]
      resources = sort(tolist(var.bedrock_knowledge_base_arns))
    }
  }

  dynamic "statement" {
    for_each = var.enable_appconfig_access ? [1] : []

    content {
      sid       = "ReadAppConfig"
      effect    = "Allow"
      # AppConfig Data API uses the appconfig IAM namespace for these actions.
      actions   = ["appconfig:StartConfigurationSession", "appconfig:GetLatestConfiguration"]
      resources = ["*"]
    }
  }
}

resource "aws_iam_role_policy" "apprunner_instance" {
  name   = "runtime-access"
  role   = aws_iam_role.apprunner_instance.id
  policy = data.aws_iam_policy_document.apprunner_instance.json
}

resource "aws_apprunner_auto_scaling_configuration_version" "backend" {
  auto_scaling_configuration_name = "${var.project_name}-${var.environment}-as"
  max_concurrency                 = var.max_concurrency
  max_size                        = var.max_size
  min_size                        = var.min_size
}

resource "aws_apprunner_service" "backend" {
  count = var.create_service ? 1 : 0

  service_name = "${var.project_name}-${var.environment}-backend"
  tags         = var.tags

  lifecycle {
    precondition {
      condition     = var.egress_type == "DEFAULT" || var.vpc_connector_arn != null
      error_message = "vpc_connector_arn is required when App Runner egress_type is VPC."
    }
  }

  source_configuration {
    auto_deployments_enabled = false

    authentication_configuration {
      access_role_arn = aws_iam_role.apprunner_ecr_access.arn
    }

    image_repository {
      image_identifier      = "${aws_ecr_repository.backend.repository_url}:${var.image_tag}"
      image_repository_type = "ECR"

      image_configuration {
        port                          = tostring(var.container_port)
        runtime_environment_variables = var.runtime_environment_variables
        runtime_environment_secrets   = var.runtime_environment_secrets
      }
    }
  }

  instance_configuration {
    cpu               = var.cpu
    memory            = var.memory
    instance_role_arn = aws_iam_role.apprunner_instance.arn
  }

  health_check_configuration {
    protocol            = "HTTP"
    path                = var.health_check_path
    interval            = 10
    timeout             = 5
    healthy_threshold   = 1
    # Spring Boot initializes JPA, Flyway and external AWS configuration
    # before it can answer the health endpoint on the smallest App Runner
    # instance size. Allow up to 100 seconds for the first deployment.
    unhealthy_threshold = 10
  }

  network_configuration {
    egress_configuration {
      egress_type = var.egress_type
      vpc_connector_arn = var.egress_type == "VPC" ? var.vpc_connector_arn : null
    }
  }

  auto_scaling_configuration_arn = aws_apprunner_auto_scaling_configuration_version.backend.arn
}
