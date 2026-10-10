package org.example.paymentservice.dtos;

import lombok.Getter;
import lombok.Setter;

// Published on the payment_events topic; consumed by the order service.
@Setter
@Getter
public class PaymentEventDto {
    public static final String LINK_CREATED = "LINK_CREATED";
    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";

    private String type;
    private Long orderId;
    private Long paymentId;
    private String paymentLink;
    private Long amountInPaise;
    private String gatewayPaymentId;
    private String failureReason;
}
