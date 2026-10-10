package org.example.paymentservice.paymentgateway;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PaymentGatewayChooserStrategy {

    @Autowired
    private RazorpayPaymentGateway razorpayPaymentGateway;

    @Autowired
    private StripePaymentGateway stripePaymentGateway;

    @Autowired
    private MockPaymentGateway mockPaymentGateway;

    @Value("${payment.gateway:auto}")
    private String configuredGateway;

    @Value("${razorpay.key.id:}")
    private String razorpayKeyId;

    @PostConstruct
    void logSelection() {
        log.info("Payment gateway in use: {}", getBestPaymentGateway().getType());
    }

    public IPaymentGateway getBestPaymentGateway() {
        String gatewayName = configuredGateway.trim().toLowerCase();
        if ("razorpay".equals(gatewayName)) {
            return razorpayPaymentGateway;
        } else if ("stripe".equals(gatewayName)) {
            return stripePaymentGateway;
        } else if ("mock".equals(gatewayName)) {
            return mockPaymentGateway;
        } else if (razorpayKeyId != null && !razorpayKeyId.isBlank()) {
            return razorpayPaymentGateway;
        } else {
            return mockPaymentGateway;
        }
    }
}
