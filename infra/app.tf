resource "digitalocean_app" "ricash" {
  spec {
    name   = "ricash"
    region = var.do_region

    # Backend API service
    service {
      name               = "api"
      instance_count     = 1
      instance_size_slug = "basic-xxs"
      http_port          = 8080

      image {
        registry_type = "DOCR"
        repository    = "ricash-api"
        deploy_on_push {
          enabled = true
        }
      }

      health_check {
        http_path             = "/actuator/health"
        initial_delay_seconds = 30
        period_seconds        = 15
      }

      env {
        key   = "SPRING_PROFILES_ACTIVE"
        value = "prod"
      }
      env {
        key   = "DATABASE_URL"
        value = "jdbc:postgresql://${digitalocean_database_cluster.ricash.private_host}:${digitalocean_database_cluster.ricash.port}/${digitalocean_database_db.ricash.name}?sslmode=require"
        type  = "SECRET"
      }
      env {
        key   = "DATABASE_USERNAME"
        value = digitalocean_database_user.ricash.name
        type  = "SECRET"
      }
      env {
        key   = "DATABASE_PASSWORD"
        value = digitalocean_database_user.ricash.password
        type  = "SECRET"
      }
      env {
        key   = "AUTH0_ISSUER_URI"
        value = "https://${var.auth0_domain}/"
      }
      env {
        key   = "AUTH0_AUDIENCE"
        value = var.auth0_api_audience
      }
      env {
        key   = "CORS_ALLOWED_ORIGINS"
        value = "${var.app_domain != "" ? "https://${var.app_domain}" : ""}"
      }
    }

    # Frontend static site
    static_site {
      name          = "frontend"
      build_command = "npm ci && npm run build"
      output_dir    = "/dist"

      github {
        repo           = "rslima/ricash"
        branch         = "master"
        deploy_on_push = true
      }

      source_dir = "frontend"

      env {
        key   = "VITE_AUTH_AUTHORITY"
        value = "https://${var.auth0_domain}"
      }
      env {
        key   = "VITE_AUTH_CLIENT_ID"
        value = auth0_client.ricash_spa.client_id
      }
      env {
        key   = "VITE_AUTH_AUDIENCE"
        value = var.auth0_api_audience
      }
      env {
        key   = "VITE_API_BASE_URL"
        value = var.app_domain != "" ? "https://${var.app_domain}/api" : ""
      }

      catchall_document = "index.html"
    }

    # Route API traffic to backend
    ingress {
      rule {
        component {
          name = "api"
        }
        match {
          path {
            prefix = "/api"
          }
        }
      }

      rule {
        component {
          name = "api"
        }
        match {
          path {
            prefix = "/actuator"
          }
        }
      }

      rule {
        component {
          name = "frontend"
        }
        match {
          path {
            prefix = "/"
          }
        }
      }
    }
  }
}
