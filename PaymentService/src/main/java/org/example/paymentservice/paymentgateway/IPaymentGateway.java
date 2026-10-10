package org.example.paymentservice.paymentgateway;

import org.example.paymentservice.models.Payment;
import org.example.paymentservice.models.PaymentGatewayType;

public interface IPaymentGateway {
    PaymentLinkDto createPaymentLink(Payment payment);

    PaymentGatewayType getType();
}
