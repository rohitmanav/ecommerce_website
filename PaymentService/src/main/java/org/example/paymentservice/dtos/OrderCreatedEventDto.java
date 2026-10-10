package org.example.paymentservice.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

// Published by the order service on the order_created topic.
@Setter
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderCreatedEventDto {
    private Long orderId;
    private Long userId;
    private String userEmail;
    private String userName;
    private String phoneNumber;
    private Long amountInPaise;
    private String currency;
    private String paymentMethod;
}
