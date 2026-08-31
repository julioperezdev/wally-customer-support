resource "aws_secretsmanager_secret" "this" {
  name                    = var.name
  description             = var.description
  recovery_window_in_days = var.recovery_window_in_days
  tags                    = var.tags
}

resource "aws_secretsmanager_secret_version" "initial" {
  count = var.initial_secret_json == null ? 0 : 1

  secret_id     = aws_secretsmanager_secret.this.id
  secret_string = var.initial_secret_json

  lifecycle {
    ignore_changes = [secret_string]
  }
}
