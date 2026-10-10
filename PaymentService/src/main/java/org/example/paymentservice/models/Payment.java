package org.example.paymentservice.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Setter
@Getter
@Entity
@Table(indexes = {
        @Index(columnList = "orderId"),
        @Index(columnList = "gatewayReferenceId")
})
public class Payment extends BaseModel {
    private Long orderId;
    private Long userId;
    private String customerName;
    private String customerEmail;
    private String customerPhone;

    // Amount in the smallest currency unit (paise for INR).
    private Long amount;
    private String currency = "INR";
    private String paymentMethod;

    @Enumerated(EnumType.STRING)
    private PaymentGatewayType gateway;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    @Column(length = 1000)
    private String paymentLink;

    // Payment link id at the gateway, e.g. plink_... for Razorpay.
    private String gatewayReferenceId;

    // Id of the completed payment at the gateway, e.g. pay_... for Razorpay.
    private String gatewayPaymentId;

    private String failureReason;

    private Date paidAt;
}
