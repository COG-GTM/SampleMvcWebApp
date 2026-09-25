output "alb_dns_name" {
  value = aws_lb.app.dns_name
}

output "app_url" {
  value = "http://${aws_lb.app.dns_name}"
}

output "ecr_repository_url" {
  value = aws_ecr_repository.app.repository_url
}

output "private_dns_name" {
  value = aws_route53_record.app.fqdn
}

output "secret_name" {
  value = aws_secretsmanager_secret.db_connection.name
}

output "instance_id" {
  value = local.deploy_app ? aws_instance.app[0].id : null
}

output "target_group_arn" {
  value = aws_lb_target_group.app.arn
}

output "runtime_contract" {
  description = "What the platform believes about the app (the drift lives here)"
  value = {
    runtime_image = var.app_runtime_image
    port          = var.app_port
    health_path   = var.app_health_path
    secret_key    = var.app_db_connection_env
  }
}
