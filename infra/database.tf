resource "digitalocean_database_cluster" "ricash" {
  name       = "ricash-db"
  engine     = "pg"
  version    = "17"
  size       = "db-s-1vcpu-1gb"
  region     = var.do_region
  node_count = 1
}

resource "digitalocean_database_db" "ricash" {
  cluster_id = digitalocean_database_cluster.ricash.id
  name       = "ricash"
}

resource "digitalocean_database_user" "ricash" {
  cluster_id = digitalocean_database_cluster.ricash.id
  name       = "ricash"
}

resource "digitalocean_database_firewall" "ricash" {
  cluster_id = digitalocean_database_cluster.ricash.id

  rule {
    type  = "app"
    value = digitalocean_app.ricash.id
  }
}
