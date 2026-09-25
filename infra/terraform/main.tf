terraform {
  required_version = ">= 1.5"
  required_providers {
    docker = {
      source  = "kreuzwerker/docker"
      version = "~> 3.0"
    }
  }
}

provider "docker" {}

locals {
  common_labels = {
    "com.datadoghq.tags.service" = "samplewebapp"
    "com.datadoghq.tags.env"     = var.environment
    "runtime.family"             = var.app_runtime_family
    "runtime.os"                 = var.app_os
  }
}

# The app container as the platform team deploys it (plan-only in the demo; docker-compose runs the
# equivalent locally). The image tag pins the RUNTIME — this is what drifts when the code moves runtimes.
resource "docker_image" "app_runtime" {
  name = var.app_runtime_image
}

resource "docker_container" "app" {
  name  = "samplewebapp-app"
  image = docker_image.app_runtime.image_id

  ports {
    internal = var.app_port
    external = var.app_port
  }

  env = [
    "${var.app_db_connection_env}=@secret:samplewebapp/db-connection",
    "DD_SERVICE=samplewebapp",
    "DD_ENV=${var.environment}",
  ]

  healthcheck {
    test     = ["CMD-SHELL", "curl -fsS http://localhost:${var.app_port}${var.app_health_path} || exit 1"]
    interval = "15s"
    timeout  = "5s"
    retries  = 5
  }

  dynamic "labels" {
    for_each = local.common_labels
    content {
      label = labels.key
      value = labels.value
    }
  }
}

output "runtime_contract" {
  value = {
    runtime_family = var.app_runtime_family
    runtime_image  = var.app_runtime_image
    os             = var.app_os
    port           = var.app_port
    health_path    = var.app_health_path
    db_secret_env  = var.app_db_connection_env
  }
}
