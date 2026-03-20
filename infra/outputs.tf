output "app_url" {
  description = "Application URL"
  value       = "https://${digitalocean_app.ricash.default_ingress}"
}

output "api_url" {
  description = "API URL"
  value       = "https://${digitalocean_app.ricash.default_ingress}/api"
}

output "database_host" {
  description = "Database private host"
  value       = digitalocean_database_cluster.ricash.private_host
  sensitive   = true
}

output "database_port" {
  description = "Database port"
  value       = digitalocean_database_cluster.ricash.port
}

output "container_registry" {
  description = "Container registry endpoint"
  value       = digitalocean_container_registry.ricash.endpoint
}

output "auth0_client_id" {
  description = "Auth0 SPA client ID"
  value       = auth0_client.ricash_spa.client_id
}

output "app_id" {
  description = "DigitalOcean App ID (needed for CI/CD deployments)"
  value       = digitalocean_app.ricash.id
}
