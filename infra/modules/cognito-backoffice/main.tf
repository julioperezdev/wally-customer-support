locals {
  resource_server_identifier = "wcs-backoffice"

  capabilities = {
    "catalog.read"             = "Read catalog products and variants"
    "catalog.write"            = "Write catalog and stock operations"
    "catalog.media.write"      = "Manage product media"
    "orders.read"              = "Read backoffice orders"
    "orders.write"             = "Create backoffice orders and payment links"
    "human-follow-up.read"     = "Read human follow-up queue"
    "human-follow-up.write"    = "Claim and resolve human follow-ups"
    "agent-evaluation.read"    = "Read evaluation runs and metrics"
    "agent-evaluation.execute" = "Execute an evaluation run"
    "agent-registry.read"      = "Read agent registry and activations"
    "agent-registry.write"     = "Author and activate agent versions"
    "agent-registry.publish"   = "Approve, publish and retire agent versions"
    "feature-flags.read"       = "Read business feature flags"
    "feature-flags.write"      = "Publish or rollback business feature flags"
  }

  role_capabilities = {
    store-viewer = [
      "catalog.read",
      "orders.read",
      "human-follow-up.read",
      "agent-evaluation.read",
      "agent-registry.read",
      "feature-flags.read",
    ]
    store-operator = [
      "catalog.read",
      "catalog.write",
      "catalog.media.write",
      "orders.read",
      "human-follow-up.read",
      "human-follow-up.write",
      "agent-evaluation.read",
      "agent-registry.read",
      "feature-flags.read",
    ]
    order-operator = [
      "catalog.read",
      "orders.read",
      "orders.write",
    ]
    agent-operator = [
      "agent-evaluation.read",
      "agent-evaluation.execute",
      "agent-registry.read",
      "agent-registry.write",
      "agent-registry.publish",
      "feature-flags.read",
      "feature-flags.write",
    ]
    admin = keys(local.capabilities)
  }

  hosted_ui_enabled = var.domain_prefix != null
}

resource "aws_cognito_user_pool" "backoffice" {
  name                = "${var.project_name}-${var.environment}-backoffice"
  deletion_protection = var.deletion_protection

  username_attributes      = ["email"]
  auto_verified_attributes = ["email"]

  password_policy {
    minimum_length                   = 12
    require_lowercase                = true
    require_numbers                  = true
    require_symbols                  = true
    require_uppercase                = true
    temporary_password_validity_days = 3
  }

  admin_create_user_config {
    allow_admin_create_user_only = true
  }

  account_recovery_setting {
    recovery_mechanism {
      name     = "verified_email"
      priority = 1
    }
  }

  tags = var.tags
}

resource "aws_cognito_resource_server" "backoffice" {
  identifier   = local.resource_server_identifier
  name         = "WCS Backoffice Capabilities"
  user_pool_id = aws_cognito_user_pool.backoffice.id

  dynamic "scope" {
    for_each = local.capabilities
    content {
      scope_name        = scope.key
      scope_description = scope.value
    }
  }
}

resource "aws_cognito_user_pool_client" "backoffice" {
  name         = "${var.project_name}-${var.environment}-backoffice-web"
  user_pool_id = aws_cognito_user_pool.backoffice.id

  generate_secret = false

  explicit_auth_flows = concat(
    ["ALLOW_REFRESH_TOKEN_AUTH", "ALLOW_USER_SRP_AUTH"],
    var.enable_password_auth ? ["ALLOW_USER_PASSWORD_AUTH"] : []
  )

  allowed_oauth_flows_user_pool_client = local.hosted_ui_enabled
  allowed_oauth_flows                  = local.hosted_ui_enabled ? ["code"] : []
  # The browser client receives identity scopes only. WCS capabilities come
  # from Cognito groups, so a user cannot request every custom scope directly.
  allowed_oauth_scopes         = local.hosted_ui_enabled ? ["openid", "email", "profile"] : []
  callback_urls                = local.hosted_ui_enabled ? var.callback_urls : []
  logout_urls                  = local.hosted_ui_enabled ? var.logout_urls : []
  supported_identity_providers = local.hosted_ui_enabled ? ["COGNITO"] : []

  access_token_validity  = 60
  id_token_validity      = 60
  refresh_token_validity = 30

  token_validity_units {
    access_token  = "minutes"
    id_token      = "minutes"
    refresh_token = "days"
  }
}

resource "aws_cognito_user_group" "role" {
  for_each = local.role_capabilities

  name         = each.key
  user_pool_id = aws_cognito_user_pool.backoffice.id
  description  = "WCS backoffice role. Backend maps this group to explicit capabilities."
  precedence   = index(keys(local.role_capabilities), each.key) + 1
}

resource "aws_cognito_user_pool_domain" "backoffice" {
  count = local.hosted_ui_enabled ? 1 : 0

  domain       = var.domain_prefix
  user_pool_id = aws_cognito_user_pool.backoffice.id
}
