resource "auth0_resource_server" "ricash_api" {
  name       = "Ricash API"
  identifier = var.auth0_api_audience

  signing_alg                                = "RS256"
  allow_offline_access                       = true
  token_lifetime                             = 86400
  skip_consent_for_verifiable_first_party_clients = true
}

resource "auth0_client" "ricash_spa" {
  name            = "ricash-frontend"
  app_type        = "spa"
  oidc_conformant = true

  callbacks = compact([
    var.app_domain != "" ? "https://${var.app_domain}/callback" : "",
    "http://localhost:5173/callback",
  ])
  allowed_logout_urls = compact([
    var.app_domain != "" ? "https://${var.app_domain}" : "",
    "http://localhost:5173",
  ])
  web_origins = compact([
    var.app_domain != "" ? "https://${var.app_domain}" : "",
    "http://localhost:5173",
  ])

  jwt_configuration {
    alg = "RS256"
  }

  grant_types = [
    "authorization_code",
    "refresh_token",
  ]

  refresh_token {
    rotation_type   = "rotating"
    expiration_type = "expiring"
    token_lifetime  = 2592000 # 30 days
    idle_token_lifetime = 1296000 # 15 days
  }
}

resource "auth0_action" "add_claims" {
  name    = "Add custom claims"
  runtime = "node18"

  supported_triggers {
    id      = "post-login"
    version = "v3"
  }

  code = <<-EOT
    exports.onExecutePostLogin = async (event, api) => {
      const namespace = "https://ricash.app";

      // Add preferred_username claim
      const username = event.user.username || event.user.email;
      api.accessToken.setCustomClaim("preferred_username", username);
      api.idToken.setCustomClaim("preferred_username", username);

      // Add roles claim
      const roles = event.authorization?.roles || [];
      api.accessToken.setCustomClaim(namespace + "/roles", roles);
      api.idToken.setCustomClaim(namespace + "/roles", roles);
    };
  EOT

  deploy = true
}

resource "auth0_trigger_actions" "post_login" {
  trigger = "post-login"

  actions {
    id           = auth0_action.add_claims.id
    display_name = auth0_action.add_claims.name
  }
}
