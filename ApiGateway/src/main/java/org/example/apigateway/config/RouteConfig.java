package org.example.apigateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.GatewayFilterSpec;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.route.builder.UriSpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;

@Configuration
public class RouteConfig {

    // Service name in Eureka mapped to the path prefixes it owns.
    private static final Map<String, List<String>> SERVICE_PATHS = Map.of(
            "userservice", List.of("/auth/**", "/users/**"),
            "productservice", List.of("/products/**", "/categories/**", "/search/**"),
            "cartservice", List.of("/cart/**"),
            "orderservice", List.of("/orders/**"),
            "paymentservice", List.of("/payments/**", "/stripeWebhook/**"),
            "notificationservice", List.of("/notifications/**"));

    @Value("${app.rate-limit.enabled:true}")
    private boolean rateLimitEnabled;

    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder, RedisRateLimiter redisRateLimiter, KeyResolver ipKeyResolver) {
        RouteLocatorBuilder.Builder routes = builder.routes();
        SERVICE_PATHS.forEach((service, paths) -> {
            routes.route(service, route -> route
                    .path(paths.toArray(String[]::new))
                    .filters(filters -> rateLimited(filters, redisRateLimiter, ipKeyResolver))
                    .uri("lb://" + service));
            // /userservice/v3/api-docs -> userservice's /v3/api-docs, used by the combined Swagger UI
            routes.route(service + "-api-docs", route -> route
                    .path("/" + service + "/v3/api-docs")
                    .filters(filters -> filters.setPath("/v3/api-docs"))
                    .uri("lb://" + service));
        });
        return routes.build();
    }

    private UriSpec rateLimited(GatewayFilterSpec filters, RedisRateLimiter redisRateLimiter, KeyResolver ipKeyResolver) {
        if (!rateLimitEnabled) {
            return filters;
        }
        return filters.requestRateLimiter(config -> {
            config.setRateLimiter(redisRateLimiter);
            config.setKeyResolver(ipKeyResolver);
        });
    }

    @Bean
    @Primary
    public RedisRateLimiter redisRateLimiter(@Value("${app.rate-limit.replenish-rate:20}") int replenishRate,
                                             @Value("${app.rate-limit.burst-capacity:40}") int burstCapacity) {
        return new RedisRateLimiter(replenishRate, burstCapacity);
    }

    @Bean
    @Primary
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
            String clientIp;
            if (remoteAddress != null && remoteAddress.getAddress() != null) {
                clientIp = remoteAddress.getAddress().getHostAddress();
            } else {
                clientIp = "unknown";
            }
            return Mono.just(clientIp);
        };
    }
}
