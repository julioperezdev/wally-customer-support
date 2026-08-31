locals {
  allowed_subjects = [
    "repo:${var.github_repository}:ref:refs/heads/main",
    "repo:${var.github_repository}:environment:production"
  ]
}

data "aws_iam_policy_document" "assume_role" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [var.github_oidc_provider_arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringLike"
      variable = "token.actions.githubusercontent.com:sub"
      values   = local.allowed_subjects
    }
  }
}

resource "aws_iam_role" "deploy" {
  name               = "${var.project_name}-${var.environment}-github-backend-deploy"
  assume_role_policy = data.aws_iam_policy_document.assume_role.json
  tags               = var.tags
}

data "aws_iam_policy_document" "deploy" {
  statement {
    sid    = "PushBackendImage"
    effect = "Allow"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:CompleteLayerUpload",
      "ecr:DescribeImages",
      "ecr:DescribeRepositories",
      "ecr:GetDownloadUrlForLayer",
      "ecr:InitiateLayerUpload",
      "ecr:PutImage",
      "ecr:UploadLayerPart"
    ]
    resources = [var.ecr_repository_arn]
  }

  statement {
    sid       = "AuthorizeEcr"
    effect    = "Allow"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  dynamic "statement" {
    for_each = var.apprunner_service_arn == null ? [] : [var.apprunner_service_arn]

    content {
      sid    = "DeployAppRunnerService"
      effect = "Allow"
      actions = [
        "apprunner:DescribeService",
        "apprunner:ListOperations",
        "apprunner:ResumeService",
        "apprunner:StartDeployment",
        "apprunner:UpdateService"
      ]
      resources = [statement.value]
    }
  }

  dynamic "statement" {
    for_each = var.apprunner_ecr_access_role_arn == null ? [] : [var.apprunner_ecr_access_role_arn]

    content {
      sid       = "PassAppRunnerEcrAccessRole"
      effect    = "Allow"
      actions   = ["iam:PassRole"]
      resources = [statement.value]
    }
  }
}

resource "aws_iam_role_policy" "deploy" {
  name   = "backend-ecr-apprunner-deploy"
  role   = aws_iam_role.deploy.id
  policy = data.aws_iam_policy_document.deploy.json
}
