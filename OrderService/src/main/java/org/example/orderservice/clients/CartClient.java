package org.example.orderservice.clients;

import lombok.extern.slf4j.Slf4j;
import org.example.orderservice.dtos.CartDto;
import org.example.orderservice.exceptions.ServiceUnavailableException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
public class CartClient {

    private static final String CART_URL = "http://cartservice/cart";

    @Autowired
    private RestTemplate restTemplate;

    public CartDto getCart(String authorizationHeader) {
        try {
            CartDto cartDto = restTemplate.exchange(CART_URL, HttpMethod.GET,
                    new HttpEntity<>(headers(authorizationHeader)), CartDto.class).getBody();
            if (cartDto == null) {
                return new CartDto();
            }
            return cartDto;
        } catch (RestClientException | IllegalStateException exception) {
            throw new ServiceUnavailableException("Cart service is unavailable");
        }
    }

    public void clearCart(String authorizationHeader) {
        try {
            restTemplate.exchange(CART_URL, HttpMethod.DELETE, new HttpEntity<>(headers(authorizationHeader)), Void.class);
        } catch (RestClientException | IllegalStateException exception) {
            log.warn("Could not clear cart after checkout: {}", exception.getMessage());
        }
    }

    private HttpHeaders headers(String authorizationHeader) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, authorizationHeader);
        return headers;
    }
}
