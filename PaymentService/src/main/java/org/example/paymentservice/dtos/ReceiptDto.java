package org.example.paymentservice.dtos;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Date;

@Setter
@Getter
public class ReceiptDto {
    private String receiptNumber;
    private Long paymentId;
    private Long orderId;
    private String customerName;
    private String customerEmail;
    private BigDecimal amount;
    private String currency;
    private String gateway;
    private String paymentMethod;
    private String gatewayPaymentId;
    private Date paidAt;
}
