package org.example.orderservice.dtos;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class PaymentRequestDto {
    private Long amount;
    private Long orderId;
    private String phoneNumber;
    private String name;
    private String email;
    private String paymentMethod;
}
