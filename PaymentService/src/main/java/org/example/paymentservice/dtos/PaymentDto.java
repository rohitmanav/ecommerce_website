package org.example.paymentservice.dtos;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Setter
@Getter
public class PaymentDto {
    private Long id;
    private Long orderId;
    // Amount in paise.
    private Long amount;
    private String currency;
    private String status;
    private String gateway;
    private String paymentMethod;
    private String paymentLink;
    private String gatewayPaymentId;
    private String failureReason;
    private Date createdAt;
    private Date paidAt;
}
