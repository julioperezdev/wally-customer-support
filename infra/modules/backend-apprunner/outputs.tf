output "ecr_repository_name" {
  value = aws_ecr_repository.backend.name
}

output "ecr_repository_arn" {
  value = aws_ecr_repository.backend.arn
}

output "ecr_repository_url" {
  value = aws_ecr_repository.backend.repository_url
}

output "apprunner_service_arn" {
  value = try(aws_apprunner_service.backend[0].arn, null)
}

output "apprunner_service_url" {
  value = try(aws_apprunner_service.backend[0].service_url, null)
}

output "apprunner_ecr_access_role_arn" {
  value = aws_iam_role.apprunner_ecr_access.arn
}

output "apprunner_instance_role_arn" {
  value = aws_iam_role.apprunner_instance.arn
}
