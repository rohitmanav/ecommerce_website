package org.example.apigateway.filters;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicPathsTest {

    @Test
    void browsingAndAuthEndpointsArePublic() {
        assertTrue(PublicPaths.isPublic(HttpMethod.POST, "/auth/login"));
        assertTrue(PublicPaths.isPublic(HttpMethod.GET, "/products"));
        assertTrue(PublicPaths.isPublic(HttpMethod.GET, "/products/5"));
        assertTrue(PublicPaths.isPublic(HttpMethod.GET, "/categories/2/products"));
        assertTrue(PublicPaths.isPublic(HttpMethod.GET, "/search"));
        assertTrue(PublicPaths.isPublic(HttpMethod.POST, "/search"));
        assertTrue(PublicPaths.isPublic(HttpMethod.GET, "/payments/callback/razorpay"));
        assertTrue(PublicPaths.isPublic(HttpMethod.POST, "/payments/webhook/razorpay"));
        assertTrue(PublicPaths.isPublic(HttpMethod.GET, "/payments/mock/3/pay"));
        assertTrue(PublicPaths.isPublic(HttpMethod.GET, "/productservice/v3/api-docs"));
        assertTrue(PublicPaths.isPublic(HttpMethod.OPTIONS, "/cart"));
    }

    @Test
    void writesAndPersonalDataNeedAToken() {
        assertFalse(PublicPaths.isPublic(HttpMethod.POST, "/products"));
        assertFalse(PublicPaths.isPublic(HttpMethod.DELETE, "/products/5"));
        assertFalse(PublicPaths.isPublic(HttpMethod.POST, "/search/reindex"));
        assertFalse(PublicPaths.isPublic(HttpMethod.GET, "/cart"));
        assertFalse(PublicPaths.isPublic(HttpMethod.POST, "/orders/checkout"));
        assertFalse(PublicPaths.isPublic(HttpMethod.GET, "/users/me"));
        assertFalse(PublicPaths.isPublic(HttpMethod.GET, "/payments/7/receipt"));
        assertFalse(PublicPaths.isPublic(HttpMethod.GET, "/notifications"));
    }
}
