package org.example.orderservice.dtos;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Setter
@Getter
public class OrderDto {
    private Long id;
    private Long userId;
    private String status;
    private Double totalAmount;
    private String currency;
    private String paymentMethod;
    private AddressDto deliveryAddress;
    private Long paymentId;
    private String paymentLink;
    private List<OrderItemDto> items = new ArrayList<>();
    private Date createdAt;
    private Date lastUpdatedAt;
}
