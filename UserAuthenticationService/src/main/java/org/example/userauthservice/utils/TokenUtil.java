package org.example.userauthservice.utils;

import org.example.userauthservice.exceptions.InvalidTokenException;

public class TokenUtil {

    private static final String BEARER_PREFIX = "Bearer ";

    public static String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new InvalidTokenException("Authorization header is missing");
        }
        if (authorizationHeader.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        }
        return authorizationHeader.trim();
    }
}
