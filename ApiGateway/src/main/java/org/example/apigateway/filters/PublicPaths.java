package org.example.apigateway.filters;

import org.springframework.http.HttpMethod;
import org.springframework.util.AntPathMatcher;

import java.util.List;

// Requests that reach the services without a bearer token.
public final class PublicPaths {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private static final List<String> ANY_METHOD = List.of(
            "/auth/**",
            "/payments/callback/**",
            "/payments/webhook/**",
            "/payments/mock/**",
            "/stripeWebhook/**",
            "/*service/v3/api-docs");

    private static final List<String> READ_ONLY = List.of(
            "/products/**",
            "/categories/**",
            "/search/**");

    private PublicPaths() {
    }

    public static boolean isPublic(HttpMethod method, String path) {
        if (HttpMethod.OPTIONS.equals(method)) {
            return true;
        }
        if (matchesAny(ANY_METHOD, path)) {
            return true;
        }
        if (HttpMethod.GET.equals(method) && matchesAny(READ_ONLY, path)) {
            return true;
        }
        return HttpMethod.POST.equals(method) && PATH_MATCHER.match("/search", path);
    }

    private static boolean matchesAny(List<String> patterns, String path) {
        for (String pattern : patterns) {
            if (PATH_MATCHER.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }
}
