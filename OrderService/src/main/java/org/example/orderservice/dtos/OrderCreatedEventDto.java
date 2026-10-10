package org.example.orderservice.dtos;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
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
