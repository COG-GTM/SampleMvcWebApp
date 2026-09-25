# Runtime contract for SampleWebApp. These pins are the source of truth the platform team deploys from;
# Dockerfile, nginx upstream, firewall allowlist and the app's own TargetFramework must agree with them.
# scripts/verify.sh cross-checks them.

variable "app_runtime_family" {
  description = "Runtime family the app image is built for. One of: netframework-4.x | dotnet-8"
  type        = string
  default     = "netframework-4.x"
}

variable "app_runtime_image" {
  description = "Base image the production app container runs on"
  type        = string
  default     = "mcr.microsoft.com/dotnet/framework/aspnet:4.8-windowsservercore-ltsc2022"
}

variable "app_os" {
  description = "Container OS the node pool must provide (windows | linux)"
  type        = string
  default     = "windows"
}

variable "app_port" {
  description = "Port the app listens on inside the container (IIS: 80, Kestrel default: 8080)"
  type        = number
  default     = 80
}

variable "app_health_path" {
  description = "HTTP path the load balancer probes"
  type        = string
  default     = "/Home/Health"
}

variable "app_db_connection_env" {
  description = "Environment variable name the app reads its SQL connection string from"
  type        = string
  default     = "SAMPLEWEBAPP_DB_CONNECTION"
}

variable "environment" {
  type    = string
  default = "demo"
}
