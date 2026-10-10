package org.example.orderservice.dtos;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Setter
@Getter
public class OrderStatusEventDto {
    private Long orderId;
    private Long userId;
    private String status;
    private Double totalAmount;
    private String currency;
    private Date occurredAt;
}
