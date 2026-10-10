package org.example.paymentservice.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.paymentservice.models.Payment;
import org.example.paymentservice.models.PaymentStatus;
import org.example.paymentservice.services.IPaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

// Called by payment gateways or the customer's browser, so these endpoints carry no bearer token.
@Tag(name = "Payment callbacks", description = "Gateway redirects, webhooks and the mock gateway's pay page")
@SecurityRequirements
@RestController
@RequestMapping("/payments")
public class PaymentCallbackController {

    @Autowired
    private IPaymentService paymentService;

    @Operation(summary = "Razorpay redirects the customer here after paying a payment link")
    @GetMapping("/callback/razorpay")
    public ResponseEntity<String> razorpayCallback(@RequestParam Map<String, String> params) {
        return resultPage(paymentService.handleRazorpayCallback(params));
    }

    @Operation(summary = "Razorpay webhook (payment_link.paid, payment_link.expired, payment_link.cancelled)")
    @PostMapping("/webhook/razorpay")
    public ResponseEntity<Void> razorpayWebhook(@RequestBody String payload,
                                                @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {
        paymentService.handleRazorpayWebhook(payload, signature);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Stripe redirects the customer here after paying; confirmation arrives via the Stripe webhook")
    @GetMapping("/callback/stripe")
    public ResponseEntity<String> stripeCallback(@RequestParam("payment_id") Long paymentId) {
        return page("Payment submitted",
                "Thank you! Payment " + paymentId + " is being confirmed. You will receive a receipt by email shortly.");
    }

    @Operation(summary = "Mock gateway pay page: completes a mock payment (outcome=success or failure)")
    @GetMapping("/mock/{paymentId}/pay")
    public ResponseEntity<String> mockPay(@PathVariable Long paymentId,
                                          @RequestParam(defaultValue = "success") String outcome) {
        return resultPage(paymentService.completeMockPayment(paymentId, !"failure".equalsIgnoreCase(outcome)));
    }

    private ResponseEntity<String> resultPage(Payment payment) {
        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            return page("Payment successful",
                    "Payment for order #" + payment.getOrderId() + " was received. Your order is confirmed and a receipt is on its way to your email.");
        }
        if (payment.getStatus() == PaymentStatus.FAILED) {
            return page("Payment failed", "Payment for order #" + payment.getOrderId() + " did not go through. Please try again.");
        }
        return page("Payment pending", "Payment for order #" + payment.getOrderId() + " is still being processed.");
    }

    private ResponseEntity<String> page(String title, String message) {
        String html = "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>" + title + "</title></head>"
                + "<body style=\"font-family:sans-serif;max-width:640px;margin:48px auto\">"
                + "<h1>" + title + "</h1><p>" + message + "</p></body></html>";
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
    }
}
