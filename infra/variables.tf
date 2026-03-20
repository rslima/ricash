variable "do_token" {
  description = "DigitalOcean API token"
  type        = string
  sensitive   = true
}

variable "do_region" {
  description = "DigitalOcean region"
  type        = string
  default     = "sfo3"
}

variable "auth0_domain" {
  description = "Auth0 tenant domain (e.g. tenant.us.auth0.com)"
  type        = string
}

variable "auth0_client_id" {
  description = "Auth0 Management API client ID (for Terraform provider)"
  type        = string
  sensitive   = true
}

variable "auth0_client_secret" {
  description = "Auth0 Management API client secret (for Terraform provider)"
  type        = string
  sensitive   = true
}

variable "auth0_api_audience" {
  description = "Auth0 API audience identifier (e.g. https://api.ricash.app)"
  type        = string
  default     = "https://api.ricash.app"
}

variable "app_domain" {
  description = "Custom domain for the application (optional)"
  type        = string
  default     = ""
}

variable "environment" {
  description = "Environment name"
  type        = string
  default     = "production"
}
