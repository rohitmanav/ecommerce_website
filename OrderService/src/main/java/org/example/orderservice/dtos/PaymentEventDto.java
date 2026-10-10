package org.example.orderservice.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentEventDto {
    // LINK_CREATED, SUCCESS or FAILED
    private String type;
    private Long orderId;
    private Long paymentId;
    private String paymentLink;
    private Long amountInPaise;
    private String gatewayPaymentId;
    private String failureReason;
}
