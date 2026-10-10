package org.example.paymentservice.paymentgateway;

import com.razorpay.PaymentLink;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import org.example.paymentservice.exceptions.PaymentGatewayException;
import org.example.paymentservice.models.Payment;
import org.example.paymentservice.models.PaymentGatewayType;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@Component
public class RazorpayPaymentGateway implements IPaymentGateway {

    private static final Map<String, String> CHECKOUT_METHODS = Map.of(
            "CARD", "card",
            "NET_BANKING", "netbanking",
            "UPI", "upi",
            "WALLET", "wallet");

    @Autowired
    private RazorpayClient razorpayClient;

    @Value("${app.payment.callback-base-url}")
    private String callbackBaseUrl;

    @Value("${app.payment.link-expiry-minutes:60}")
    private long linkExpiryMinutes;

    @Override
    public PaymentLinkDto createPaymentLink(Payment payment) {
        try {
            PaymentLink paymentLink = razorpayClient.paymentLink.create(buildPaymentLinkRequest(payment));
            return new PaymentLinkDto(paymentLink.get("short_url"), paymentLink.get("id"));
        } catch (RazorpayException exception) {
            throw new PaymentGatewayException("Razorpay could not create the payment link: " + exception.getMessage());
        }
    }

    JSONObject buildPaymentLinkRequest(Payment payment) {
        JSONObject paymentLinkRequest = new JSONObject();
        paymentLinkRequest.put("amount", payment.getAmount());
        paymentLinkRequest.put("currency", payment.getCurrency());
        paymentLinkRequest.put("accept_partial", false);
        // Razorpay requires the expiry to be at least 15 minutes in the future
        paymentLinkRequest.put("expire_by", Instant.now().plus(Math.max(linkExpiryMinutes, 16), ChronoUnit.MINUTES).getEpochSecond());
        paymentLinkRequest.put("reference_id", "payment_" + payment.getId());
        paymentLinkRequest.put("description", "Payment for order #" + payment.getOrderId());
        JSONObject customer = new JSONObject();
        if (payment.getCustomerName() != null) {
            customer.put("name", payment.getCustomerName());
        }
        if (payment.getCustomerPhone() != null) {
            customer.put("contact", payment.getCustomerPhone());
        }
        if (payment.getCustomerEmail() != null) {
            customer.put("email", payment.getCustomerEmail());
        }
        paymentLinkRequest.put("customer", customer);
        JSONObject notify = new JSONObject();
        notify.put("sms", payment.getCustomerPhone() != null);
        notify.put("email", payment.getCustomerEmail() != null);
        paymentLinkRequest.put("notify", notify);
        paymentLinkRequest.put("reminder_enable", true);
        JSONObject notes = new JSONObject();
        notes.put("order_id", String.valueOf(payment.getOrderId()));
        notes.put("payment_id", String.valueOf(payment.getId()));
        paymentLinkRequest.put("notes", notes);
        paymentLinkRequest.put("callback_url", callbackBaseUrl + "/payments/callback/razorpay");
        paymentLinkRequest.put("callback_method", "get");

        // Show only the method the customer picked at checkout; unknown or missing means all methods.
        String chosenMethod = null;
        if (payment.getPaymentMethod() != null) {
            chosenMethod = CHECKOUT_METHODS.get(payment.getPaymentMethod().toUpperCase());
        }
        if (chosenMethod != null) {
            JSONObject methods = new JSONObject();
            for (String method : CHECKOUT_METHODS.values()) {
                if (method.equals(chosenMethod)) {
                    methods.put(method, "1");
                } else {
                    methods.put(method, "0");
                }
            }
            JSONObject checkout = new JSONObject();
            checkout.put("method", methods);
            JSONObject options = new JSONObject();
            options.put("checkout", checkout);
            paymentLinkRequest.put("options", options);
        }
        return paymentLinkRequest;
    }

    @Override
    public PaymentGatewayType getType() {
        return PaymentGatewayType.RAZORPAY;
    }
}
