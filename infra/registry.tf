resource "digitalocean_container_registry" "ricash" {
  name                   = "ricash"
  subscription_tier_slug = "starter"
  region                 = var.do_region
}
