output "user_pool_id" {
  value = aws_cognito_user_pool.backoffice.id
}

output "user_pool_arn" {
  value = aws_cognito_user_pool.backoffice.arn
}

output "client_id" {
  value = aws_cognito_user_pool_client.backoffice.id
}

output "issuer_uri" {
  value = "https://cognito-idp.${var.aws_region}.amazonaws.com/${aws_cognito_user_pool.backoffice.id}"
}

output "hosted_ui_domain" {
  value = local.hosted_ui_enabled ? "https://${var.domain_prefix}.auth.${var.aws_region}.amazoncognito.com" : null
}

output "resource_server_identifier" {
  value = local.resource_server_identifier
}

output "role_names" {
  value = sort(keys(local.role_capabilities))
}

output "role_capabilities" {
  value = local.role_capabilities
}
