package org.example.apigateway.filters;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.example.apigateway.dtos.UserDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ORIGINAL_REQUEST_URL_ATTR;

// Services still authorize each request themselves; the gateway only stops anonymous traffic early.
@Slf4j
@Component
public class AuthenticationFilter implements GlobalFilter, Ordered {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_ROLES_HEADER = "X-User-Roles";

    private static final String VALIDATE_TOKEN_URL = "http://userservice/auth/validateToken";
    private static final String BEARER_PREFIX = "Bearer ";

    private final WebClient webClient;

    @Autowired
    public AuthenticationFilter(WebClient.Builder loadBalancedWebClientBuilder) {
        this.webClient = loadBalancedWebClientBuilder.build();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(USER_ID_HEADER);
                    headers.remove(USER_ROLES_HEADER);
                })
                .build();

        if (PublicPaths.isPublic(request.getMethod(), originalPath(exchange))) {
            return chain.filter(exchange.mutate().request(request).build());
        }

        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return reject(exchange, HttpStatus.UNAUTHORIZED, "Authorization header with a Bearer token is required");
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();

        return validate(token).flatMap(validationResult -> {
            UserDto userDto = validationResult.getUserDto();
            if (userDto == null) {
                return reject(exchange, validationResult.getStatus(), validationResult.getMessage());
            }
            ServerHttpRequest authenticated = request.mutate()
                    .header(USER_ID_HEADER, String.valueOf(userDto.getUserId()))
                    .header(USER_ROLES_HEADER, String.join(",", userDto.getRoles()))
                    .build();
            return chain.filter(exchange.mutate().request(authenticated).build());
        });
    }

    // Path as sent by the client; route filters such as SetPath have already rewritten the request by now.
    private String originalPath(ServerWebExchange exchange) {
        Set<URI> originalUrls = exchange.getAttributeOrDefault(GATEWAY_ORIGINAL_REQUEST_URL_ATTR, Collections.emptySet());
        if (originalUrls.isEmpty()) {
            return exchange.getRequest().getPath().value();
        }
        return originalUrls.iterator().next().getRawPath();
    }

    private Mono<ValidationResult> validate(String token) {
        return webClient.post()
                .uri(VALIDATE_TOKEN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("token", token))
                .retrieve()
                .bodyToMono(UserDto.class)
                .timeout(Duration.ofSeconds(5))
                .map(ValidationResult::valid)
                .defaultIfEmpty(ValidationResult.invalid(HttpStatus.UNAUTHORIZED, "Token is invalid or expired"))
                .onErrorResume(exception -> {
                    if (exception instanceof WebClientResponseException responseException
                            && responseException.getStatusCode().is4xxClientError()) {
                        return Mono.just(ValidationResult.invalid(HttpStatus.UNAUTHORIZED, "Token is invalid or expired"));
                    }
                    log.warn("Could not validate token with the user service: {}", exception.getMessage());
                    return Mono.just(ValidationResult.invalid(HttpStatus.SERVICE_UNAVAILABLE, "Authentication service is unavailable"));
                });
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"status\":" + status.value()
                + ",\"message\":\"" + message.replace("\"", "'")
                + "\",\"timestamp\":\"" + Instant.now() + "\"}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        // after the route's rate limiter so rejected floods never reach the user service
        return 10;
    }

    @Getter
    @AllArgsConstructor
    private static class ValidationResult {
        private UserDto userDto;
        private HttpStatus status;
        private String message;

        static ValidationResult valid(UserDto userDto) {
            return new ValidationResult(userDto, HttpStatus.OK, null);
        }

        static ValidationResult invalid(HttpStatus status, String message) {
            return new ValidationResult(null, status, message);
        }
    }
}
