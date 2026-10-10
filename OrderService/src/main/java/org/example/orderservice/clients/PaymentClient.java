package org.example.orderservice.clients;

import lombok.extern.slf4j.Slf4j;
import org.example.orderservice.dtos.PaymentDto;
import org.example.orderservice.dtos.PaymentRequestDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
public class PaymentClient {

    private static final String PAYMENTS_URL = "http://paymentservice/payments";

    @Autowired
    private RestTemplate restTemplate;

    public PaymentDto createPayment(PaymentRequestDto paymentRequestDto, String authorizationHeader) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, authorizationHeader);
        try {
            return restTemplate.exchange(PAYMENTS_URL, HttpMethod.POST,
                    new HttpEntity<>(paymentRequestDto, headers), PaymentDto.class).getBody();
        } catch (RestClientException | IllegalStateException exception) {
            log.warn("Could not create payment for order {}: {}", paymentRequestDto.getOrderId(), exception.getMessage());
            return null;
        }
    }
}
