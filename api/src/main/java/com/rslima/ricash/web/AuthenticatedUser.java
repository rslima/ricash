package com.rslima.ricash.web;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Resolves the application user id from the authenticated JWT. */
public final class AuthenticatedUser {

    private AuthenticatedUser() {
    }

    public static String userId(JwtAuthenticationToken principal) {
        return principal.getName();
    }
}
