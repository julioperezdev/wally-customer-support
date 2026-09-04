data "aws_caller_identity" "current" {}

data "aws_partition" "current" {}

locals {
  account_id = data.aws_caller_identity.current.account_id
  partition  = data.aws_partition.current.partition

  role_name = "${var.project_name}-${var.environment}-github-terraform-deploy"

  state_bucket_arn = "arn:${local.partition}:s3:::${var.state_bucket_name}"
  state_object_arns = [
    "${local.state_bucket_arn}/${var.state_key}",
    "${local.state_bucket_arn}/${var.state_key}.tflock",
  ]

  iam_role_arn_pattern                        = "arn:${local.partition}:iam::${local.account_id}:role/${var.project_name}-${var.environment}-*"
  ecr_repository_arn_pattern                  = "arn:${local.partition}:ecr:${var.aws_region}:${local.account_id}:repository/${var.project_name}-${var.environment}-*"
  apprunner_service_arn_pattern               = "arn:${local.partition}:apprunner:${var.aws_region}:${local.account_id}:service/${var.project_name}-${var.environment}-*"
  apprunner_autoscaling_arn_pattern           = "arn:${local.partition}:apprunner:${var.aws_region}:${local.account_id}:autoscalingconfiguration/*"
  apprunner_vpc_connector_arn_pattern         = "arn:${local.partition}:apprunner:${var.aws_region}:${local.account_id}:vpcconnector/*"
  apprunner_ecr_access_role_arn               = "arn:${local.partition}:iam::${local.account_id}:role/${var.project_name}-${var.environment}-apprunner-ecr-access"
  apprunner_instance_role_arn                 = "arn:${local.partition}:iam::${local.account_id}:role/${var.project_name}-${var.environment}-apprunner-instance"
  appconfig_application_arn_pattern           = "arn:${local.partition}:appconfig:${var.aws_region}:${local.account_id}:application/*"
  appconfig_configuration_profile_arn_pattern = "arn:${local.partition}:appconfig:${var.aws_region}:${local.account_id}:application/*/configurationprofile/*"
  appconfig_environment_arn_pattern           = "arn:${local.partition}:appconfig:${var.aws_region}:${local.account_id}:application/*/environment/*"
  appconfig_deployment_arn_pattern            = "arn:${local.partition}:appconfig:${var.aws_region}:${local.account_id}:application/*/environment/*/deployment/*"
  appconfig_deployment_strategy_arn_pattern   = "arn:${local.partition}:appconfig:${var.aws_region}:${local.account_id}:deploymentstrategy/*"
  secret_arn_pattern                          = "arn:${local.partition}:secretsmanager:${var.aws_region}:${local.account_id}:secret:${var.secret_name_prefix}*"
  knowledge_base_role_arn_pattern             = "arn:${local.partition}:iam::${local.account_id}:role/${var.project_name}-${var.environment}-bedrock-kb"
  knowledge_base_arn_pattern                  = "arn:${local.partition}:bedrock:${var.aws_region}:${local.account_id}:knowledge-base/*"
  knowledge_base_data_source_arn_pattern      = "arn:${local.partition}:bedrock:${var.aws_region}:${local.account_id}:knowledge-base/*/datasource/*"
  source_bucket_arn_pattern                   = "arn:${local.partition}:s3:::${var.project_name}-${var.environment}-kb-source-*"
  source_object_arn_pattern                   = "${local.source_bucket_arn_pattern}/documents/*"
  vector_bucket_arn_pattern                   = "arn:${local.partition}:s3vectors:${var.aws_region}:${local.account_id}:bucket/${var.project_name}-${var.environment}-kb-vectors-*"
  vector_index_arn_pattern                    = "${local.vector_bucket_arn_pattern}/index/*"
  terraform_knowledge_base_policy_arn         = "arn:${local.partition}:iam::${local.account_id}:policy/${var.project_name}-${var.environment}-terraform-knowledge-base-access"
  service_linked_role_arn                     = "arn:${local.partition}:iam::${local.account_id}:role/aws-service-role/apprunner.amazonaws.com/AWSServiceRoleForAppRunner"

  legacy_allowed_subjects = [
    "repo:${var.github_repository}:ref:refs/heads/main",
    "repo:${var.github_repository}:environment:production",
  ]

  immutable_allowed_subjects = var.github_repository_owner_id == null || var.github_repository_id == null ? [] : [
    "repo:${split("/", var.github_repository)[0]}@${var.github_repository_owner_id}/${split("/", var.github_repository)[1]}@${var.github_repository_id}:ref:refs/heads/main",
    "repo:${split("/", var.github_repository)[0]}@${var.github_repository_owner_id}/${split("/", var.github_repository)[1]}@${var.github_repository_id}:environment:production",
  ]

  allowed_subjects = concat(local.legacy_allowed_subjects, local.immutable_allowed_subjects)
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

resource "aws_iam_role" "terraform" {
  name                 = local.role_name
  assume_role_policy   = data.aws_iam_policy_document.assume_role.json
  permissions_boundary = var.permissions_boundary_arn
  tags                 = var.tags
}

data "aws_iam_policy_document" "terraform" {
  statement {
    sid    = "TerraformStateObjects"
    effect = "Allow"
    actions = [
      "s3:DeleteObject",
      "s3:GetObject",
      "s3:PutObject",
    ]
    resources = local.state_object_arns
  }

  statement {
    sid    = "TerraformStateBucketMetadata"
    effect = "Allow"
    actions = [
      "s3:GetBucketLocation",
      "s3:GetBucketVersioning",
    ]
    resources = [local.state_bucket_arn]
  }

  statement {
    sid       = "TerraformStateBucketList"
    effect    = "Allow"
    actions   = ["s3:ListBucket"]
    resources = [local.state_bucket_arn]

    condition {
      test     = "StringLike"
      variable = "s3:prefix"
      values   = [var.state_key, "${var.state_key}*"]
    }
  }

  statement {
    sid    = "DiscoverRds"
    effect = "Allow"
    actions = [
      "rds:DescribeDBInstances",
    ]
    resources = ["*"]
  }

  statement {
    sid    = "CreateWcsKnowledgeSourceBucket"
    effect = "Allow"
    actions = [
      "s3:CreateBucket",
    ]
    resources = ["*"]
  }

  statement {
    sid    = "ReadWcsKnowledgeSourceBucket"
    effect = "Allow"
    actions = [
      "s3:Get*",
      "s3:ListBucket",
    ]
    resources = [local.source_bucket_arn_pattern, local.source_object_arn_pattern]
  }

  statement {
    sid    = "CreateWcsKnowledgeVectorResources"
    effect = "Allow"
    actions = [
      "s3vectors:CreateIndex",
      "s3vectors:CreateVectorBucket",
    ]
    resources = ["*"]
  }

  statement {
    sid    = "ManageWcsKnowledgeVectorResources"
    effect = "Allow"
    actions = [
      "s3vectors:DeleteIndex",
      "s3vectors:DeleteVectorBucket",
      "s3vectors:GetIndex",
      "s3vectors:GetVectorBucket",
      "s3vectors:GetVectorBucketPolicy",
      "s3vectors:ListIndexes",
      "s3vectors:PutVectorBucketPolicy",
      "s3vectors:TagResource",
      "s3vectors:UntagResource",
    ]
    resources = [local.vector_bucket_arn_pattern, local.vector_index_arn_pattern]
  }

  statement {
    sid    = "CreateWcsKnowledgeBaseResources"
    effect = "Allow"
    actions = [
      "bedrock:CreateDataSource",
      "bedrock:CreateKnowledgeBase",
    ]
    resources = ["*"]
  }

  statement {
    sid    = "ManageWcsKnowledgeBaseResources"
    effect = "Allow"
    actions = [
      "bedrock:GetDataSource",
      "bedrock:GetKnowledgeBase",
      "bedrock:ListDataSources",
      "bedrock:ListTagsForResource",
      "bedrock:StartIngestionJob",
      "bedrock:TagResource",
      "bedrock:UntagResource",
      "bedrock:UpdateDataSource",
      "bedrock:UpdateKnowledgeBase",
    ]
    resources = [local.knowledge_base_arn_pattern, local.knowledge_base_data_source_arn_pattern]
  }

  statement {
    sid    = "DiscoverAppConfig"
    effect = "Allow"
    actions = [
      "appconfig:ListApplications",
      "appconfig:ListDeploymentStrategies",
    ]
    resources = ["*"]
  }

  statement {
    sid    = "DiscoverWcsAppConfigResources"
    effect = "Allow"
    actions = [
      "appconfig:ListConfigurationProfiles",
      "appconfig:ListDeployments",
      "appconfig:ListEnvironments",
      "appconfig:ListHostedConfigurationVersions",
    ]
    resources = [
      local.appconfig_application_arn_pattern,
      local.appconfig_configuration_profile_arn_pattern,
      local.appconfig_environment_arn_pattern,
    ]
  }

  statement {
    sid    = "CreateWcsAppConfigResources"
    effect = "Allow"
    actions = [
      "appconfig:CreateApplication",
      "appconfig:CreateDeploymentStrategy",
    ]
    resources = ["*"]
  }

  statement {
    sid    = "ManageWcsAppConfigResources"
    effect = "Allow"
    actions = [
      "appconfig:CreateConfigurationProfile",
      "appconfig:CreateEnvironment",
      "appconfig:CreateHostedConfigurationVersion",
      "appconfig:GetApplication",
      "appconfig:GetConfigurationProfile",
      "appconfig:GetDeployment",
      "appconfig:GetDeploymentStrategy",
      "appconfig:GetEnvironment",
      "appconfig:GetHostedConfigurationVersion",
      "appconfig:ListTagsForResource",
      "appconfig:StartDeployment",
      "appconfig:TagResource",
      "appconfig:UntagResource",
      "appconfig:UpdateApplication",
      "appconfig:UpdateConfigurationProfile",
      "appconfig:UpdateDeploymentStrategy",
      "appconfig:UpdateEnvironment",
      "appconfig:ValidateConfiguration",
    ]
    resources = [
      local.appconfig_application_arn_pattern,
      local.appconfig_configuration_profile_arn_pattern,
      local.appconfig_environment_arn_pattern,
      local.appconfig_deployment_arn_pattern,
      local.appconfig_deployment_strategy_arn_pattern,
    ]
  }

  statement {
    sid    = "CreateWcsSecrets"
    effect = "Allow"
    actions = [
      "secretsmanager:CreateSecret",
    ]
    resources = ["*"]

    condition {
      test     = "StringLike"
      variable = "secretsmanager:Name"
      values   = ["${var.secret_name_prefix}*"]
    }
  }

  statement {
    sid    = "ManageWcsSecrets"
    effect = "Allow"
    actions = [
      "secretsmanager:DescribeSecret",
      "secretsmanager:GetResourcePolicy",
      "secretsmanager:GetSecretValue",
      "secretsmanager:ListSecretVersionIds",
      "secretsmanager:PutSecretValue",
      "secretsmanager:TagResource",
      "secretsmanager:UntagResource",
      "secretsmanager:UpdateSecret",
      "secretsmanager:UpdateSecretVersionStage",
    ]
    resources = [local.secret_arn_pattern]
  }

  dynamic "statement" {
    for_each = var.shared_rds_secret_arn == null ? [] : [var.shared_rds_secret_arn]

    content {
      sid    = "ReadSharedRdsSecretMetadata"
      effect = "Allow"
      actions = [
        "secretsmanager:DescribeSecret",
        "secretsmanager:GetResourcePolicy",
      ]
      resources = [statement.value]
    }
  }

  statement {
    sid    = "CreateWcsEcrRepository"
    effect = "Allow"
    actions = [
      "ecr:CreateRepository",
    ]
    resources = ["*"]
  }

  statement {
    sid    = "ManageWcsEcrRepository"
    effect = "Allow"
    actions = [
      "ecr:DescribeRepositories",
      "ecr:DeleteLifecyclePolicy",
      "ecr:GetLifecyclePolicy",
      "ecr:ListTagsForResource",
      "ecr:PutLifecyclePolicy",
      "ecr:TagResource",
      "ecr:UntagResource",
    ]
    resources = [local.ecr_repository_arn_pattern]
  }

  statement {
    sid    = "CreateWcsAppRunnerResources"
    effect = "Allow"
    actions = [
      "apprunner:CreateAutoScalingConfiguration",
      "apprunner:CreateService",
      "apprunner:CreateVpcConnector",
    ]
    resources = ["*"]
  }

  statement {
    sid    = "ManageWcsAppRunnerResources"
    effect = "Allow"
    actions = [
      "apprunner:DescribeAutoScalingConfiguration",
      "apprunner:DescribeService",
      "apprunner:DescribeVpcConnector",
      "apprunner:ListOperations",
      "apprunner:ListTagsForResource",
      "apprunner:PauseService",
      "apprunner:ResumeService",
      "apprunner:StartDeployment",
      "apprunner:TagResource",
      "apprunner:UntagResource",
      "apprunner:UpdateService",
    ]
    resources = [
      local.apprunner_service_arn_pattern,
      local.apprunner_autoscaling_arn_pattern,
      local.apprunner_vpc_connector_arn_pattern,
    ]
  }

  statement {
    sid    = "DiscoverAppRunnerResources"
    effect = "Allow"
    actions = [
      "apprunner:ListAutoScalingConfigurations",
      "apprunner:ListServices",
      "apprunner:ListVpcConnectors",
    ]
    resources = ["*"]
  }

  statement {
    sid    = "CreateWcsIamRoles"
    effect = "Allow"
    actions = [
      "iam:CreateRole",
    ]
    resources = ["*"]

    condition {
      test     = "StringLike"
      variable = "iam:RoleName"
      values   = ["${var.project_name}-${var.environment}-*"]
    }
  }

  statement {
    sid    = "ManageWcsIamRoles"
    effect = "Allow"
    actions = [
      "iam:AttachRolePolicy",
      "iam:GetRole",
      "iam:GetRolePolicy",
      "iam:ListAttachedRolePolicies",
      "iam:ListRolePolicies",
      "iam:PutRolePolicy",
      "iam:TagRole",
      "iam:UntagRole",
      "iam:UpdateAssumeRolePolicy",
    ]
    resources = [local.iam_role_arn_pattern]
  }

  statement {
    sid       = "CreateWcsManagedPolicies"
    effect    = "Allow"
    actions   = ["iam:CreatePolicy"]
    resources = ["*"]

    condition {
      test     = "StringEquals"
      variable = "iam:PolicyName"
      values   = ["${var.project_name}-${var.environment}-terraform-knowledge-base-access"]
    }
  }

  statement {
    sid    = "ManageWcsManagedPolicies"
    effect = "Allow"
    actions = [
      "iam:CreatePolicyVersion",
      "iam:DeletePolicyVersion",
      "iam:GetPolicy",
      "iam:GetPolicyVersion",
      "iam:ListPolicyVersions",
      "iam:TagPolicy",
      "iam:UntagPolicy",
    ]
    resources = [local.terraform_knowledge_base_policy_arn]
  }

  statement {
    sid       = "PassWcsAppRunnerRoles"
    effect    = "Allow"
    actions   = ["iam:PassRole"]
    resources = [local.apprunner_ecr_access_role_arn, local.apprunner_instance_role_arn]

    condition {
      test     = "StringEquals"
      variable = "iam:PassedToService"
      values   = ["apprunner.amazonaws.com"]
    }
  }

  statement {
    sid       = "PassWcsKnowledgeBaseRole"
    effect    = "Allow"
    actions   = ["iam:PassRole"]
    resources = [local.knowledge_base_role_arn_pattern]

    condition {
      test     = "StringEquals"
      variable = "iam:PassedToService"
      values   = ["bedrock.amazonaws.com"]
    }
  }

  statement {
    sid       = "CreateAppRunnerServiceLinkedRole"
    effect    = "Allow"
    actions   = ["iam:CreateServiceLinkedRole"]
    resources = [local.service_linked_role_arn]

    condition {
      test     = "StringEquals"
      variable = "iam:AWSServiceName"
      values   = ["apprunner.amazonaws.com"]
    }
  }

  statement {
    sid    = "DenyWcsResourceDeletion"
    effect = "Deny"
    actions = [
      "apprunner:DeleteAutoScalingConfiguration",
      "apprunner:DeleteService",
      "apprunner:DeleteVpcConnector",
      "appconfig:DeleteApplication",
      "appconfig:DeleteConfigurationProfile",
      "appconfig:DeleteDeploymentStrategy",
      "appconfig:DeleteEnvironment",
      "appconfig:DeleteHostedConfigurationVersion",
      "ecr:DeleteRepository",
      "iam:DeleteRole",
      "iam:DeleteRolePolicy",
      "iam:DetachRolePolicy",
      "secretsmanager:DeleteSecret",
      "s3:DeleteBucket",
      "s3:DeleteObject",
      "s3vectors:DeleteIndex",
      "s3vectors:DeleteVectorBucket",
      "bedrock:DeleteDataSource",
      "bedrock:DeleteKnowledgeBase",
    ]
    resources = [
      local.apprunner_service_arn_pattern,
      local.apprunner_autoscaling_arn_pattern,
      local.apprunner_vpc_connector_arn_pattern,
      local.appconfig_application_arn_pattern,
      local.appconfig_configuration_profile_arn_pattern,
      local.appconfig_environment_arn_pattern,
      local.appconfig_deployment_arn_pattern,
      local.appconfig_deployment_strategy_arn_pattern,
      local.ecr_repository_arn_pattern,
      local.iam_role_arn_pattern,
      local.secret_arn_pattern,
      local.source_bucket_arn_pattern,
      local.source_object_arn_pattern,
      local.vector_bucket_arn_pattern,
      local.vector_index_arn_pattern,
      local.knowledge_base_arn_pattern,
      local.knowledge_base_data_source_arn_pattern,
    ]
  }
}

data "aws_iam_policy_document" "terraform_knowledge_base" {
  statement {
    sid    = "ManageWcsKnowledgeSourceBucket"
    effect = "Allow"
    actions = [
      "s3:PutBucketOwnershipControls",
      "s3:PutBucketPublicAccessBlock",
      "s3:PutBucketTagging",
      "s3:PutBucketVersioning",
      "s3:PutEncryptionConfiguration",
      "s3:PutLifecycleConfiguration",
      "s3:PutObject",
      "s3:TagResource",
      "s3:UntagResource",
    ]
    resources = [local.source_bucket_arn_pattern, local.source_object_arn_pattern]
  }
}

resource "aws_iam_role_policy" "terraform" {
  name   = "terraform-wcs-scoped-access"
  role   = aws_iam_role.terraform.id
  policy = data.aws_iam_policy_document.terraform.json
}

resource "aws_iam_policy" "terraform_knowledge_base" {
  name        = "${var.project_name}-${var.environment}-terraform-knowledge-base-access"
  description = "Terraform access to the WCS Knowledge Base source bucket."
  policy      = data.aws_iam_policy_document.terraform_knowledge_base.json
  tags        = var.tags
}

resource "aws_iam_role_policy_attachment" "terraform_knowledge_base" {
  role       = aws_iam_role.terraform.name
  policy_arn = aws_iam_policy.terraform_knowledge_base.arn
}
