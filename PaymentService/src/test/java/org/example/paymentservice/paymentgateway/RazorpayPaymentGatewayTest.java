package org.example.paymentservice.paymentgateway;

import org.example.paymentservice.models.Payment;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class RazorpayPaymentGatewayTest {

    private RazorpayPaymentGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new RazorpayPaymentGateway();
        ReflectionTestUtils.setField(gateway, "callbackBaseUrl", "http://localhost:8080");
        ReflectionTestUtils.setField(gateway, "linkExpiryMinutes", 60L);
    }

    @Test
    void requestUsesCustomerDetailsInTheRightFieldsAndAFutureExpiry() {
        JSONObject request = gateway.buildPaymentLinkRequest(payment("UPI"));

        JSONObject customer = request.getJSONObject("customer");
        assertEquals("Asha", customer.getString("name"));
        assertEquals("+919876543210", customer.getString("contact"));
        assertEquals(49900L, request.getLong("amount"));
        assertFalse(request.getBoolean("accept_partial"));
        assertTrue(request.getLong("expire_by") > Instant.now().getEpochSecond() + 15 * 60);
        assertEquals("http://localhost:8080/payments/callback/razorpay", request.getString("callback_url"));
    }

    @Test
    void checkoutShowsOnlyTheChosenPaymentMethod() {
        JSONObject methods = gateway.buildPaymentLinkRequest(payment("NET_BANKING"))
                .getJSONObject("options").getJSONObject("checkout").getJSONObject("method");

        assertEquals("1", methods.getString("netbanking"));
        assertEquals("0", methods.getString("card"));
        assertEquals("0", methods.getString("upi"));
        assertEquals("0", methods.getString("wallet"));
    }

    @Test
    void allMethodsAreOfferedWhenNoneWasChosen() {
        assertFalse(gateway.buildPaymentLinkRequest(payment(null)).has("options"));
    }

    private Payment payment(String paymentMethod) {
        Payment payment = new Payment();
        payment.setId(5L);
        payment.setOrderId(10L);
        payment.setAmount(49900L);
        payment.setCustomerName("Asha");
        payment.setCustomerPhone("+919876543210");
        payment.setCustomerEmail("asha@test.local");
        payment.setPaymentMethod(paymentMethod);
        return payment;
    }
}
