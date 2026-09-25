variable "region" {
  type    = string
  default = "us-east-1"
}

variable "project" {
  type    = string
  default = "modernization-demo"
}

variable "track" {
  type    = string
  default = "dotnet"
}

variable "owner" {
  type    = string
  default = "devin-demo"
}

variable "name" {
  description = "Resource name prefix"
  type        = string
  default     = "samplewebapp"
}

variable "instance_type" {
  description = "t3.small is the requested floor; SQL Server 2022 in a container needs >= 2 GiB, so the DB+app host defaults to t3.medium"
  type        = string
  default     = "t3.medium"
}

variable "dns_zone" {
  description = "Private hosted zone attached to the VPC (no public domain available)"
  type        = string
  default     = "demo.internal"
}

# ---- Legacy runtime contract. Mirrors infra/terraform/variables.tf (docker provider). These values
# are the planted drift: they describe the .NET Framework/IIS app and are NOT updated when the code
# moves to ASP.NET Core (Kestrel :8080, /health, appsettings key).
variable "app_runtime_family" {
  type    = string
  default = "netframework-4.x"
}

variable "app_runtime_image" {
  description = "Production base image pinned by the platform team (Windows; Linux stand-in is infra/legacy/Dockerfile.mono)"
  type        = string
  default     = "mcr.microsoft.com/dotnet/framework/aspnet:4.8-windowsservercore-ltsc2022"
}

variable "app_port" {
  description = "Port the app container listens on (IIS: 80)"
  type        = number
  default     = 80
}

variable "app_health_path" {
  description = "Target-group health check path"
  type        = string
  default     = "/Home/Health"
}

variable "app_db_connection_env" {
  description = "Env var / Secrets Manager key name the app reads its DB connection string from"
  type        = string
  default     = "SAMPLEWEBAPP_DB_CONNECTION"
}

variable "app_image" {
  description = "ECR image URI:tag of the built legacy app container. Empty = only create the ECR repo (phase 1 of make aws-up)."
  type        = string
  default     = ""
}
