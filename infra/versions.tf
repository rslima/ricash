terraform {
  required_version = ">= 1.5"

  required_providers {
    digitalocean = {
      source  = "digitalocean/digitalocean"
      version = "~> 2.34"
    }
    auth0 = {
      source  = "auth0/auth0"
      version = "~> 1.2"
    }
  }

  backend "s3" {
    endpoints = {
      s3 = "https://sfo3.digitaloceanspaces.com"
    }
    bucket                      = "ricash-terraform-state"
    key                         = "terraform.tfstate"
    region                      = "us-east-1" # Required but unused by DO Spaces
    use_path_style              = true
    skip_credentials_validation = true
    skip_requesting_account_id  = true
    skip_metadata_api_check     = true
    skip_s3_checksum            = true
    skip_region_validation      = true
  }
}
