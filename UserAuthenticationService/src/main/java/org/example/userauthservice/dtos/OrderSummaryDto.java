package org.example.userauthservice.dtos;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Setter
@Getter
public class OrderSummaryDto {
    // Paid orders that were not cancelled.
    private long totalOrders;
    private long deliveredOrders;
    private Double totalSpent;
    private Long lastOrderId;
    private String lastOrderStatus;
    private Date lastOrderAt;
}
