package org.example.paymentservice.paymentgateway;

import org.example.paymentservice.models.Payment;
import org.example.paymentservice.models.PaymentGatewayType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MockPaymentGateway implements IPaymentGateway {

    @Value("${app.payment.callback-base-url}")
    private String callbackBaseUrl;

    @Override
    public PaymentLinkDto createPaymentLink(Payment payment) {
        return new PaymentLinkDto(callbackBaseUrl + "/payments/mock/" + payment.getId() + "/pay",
                "mock_plink_" + payment.getId());
    }

    @Override
    public PaymentGatewayType getType() {
        return PaymentGatewayType.MOCK;
    }
}
