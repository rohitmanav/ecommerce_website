package org.example.userauthservice.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Setter
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderStatusEventDto {
    private Long orderId;
    private Long userId;
    private String status;
    private Double totalAmount;
    private String currency;
    private Date occurredAt;
}
