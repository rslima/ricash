package com.rslima.ricash.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ricash.security.jwt")
/**
 * @param principalClaimName JWT claim used as the application user id
 * @param audience required {@code aud} value; blank disables the check (dev)
 */
public record JwtClaimProperties(
        String principalClaimName,
        String audience
) {
}
