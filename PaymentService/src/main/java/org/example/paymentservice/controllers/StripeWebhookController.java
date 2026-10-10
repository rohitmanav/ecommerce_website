package org.example.paymentservice.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.paymentservice.services.IPaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Payment callbacks")
@SecurityRequirements
@RestController
@RequestMapping("/stripeWebhook")
public class StripeWebhookController {

    @Autowired
    private IPaymentService paymentService;

    @Operation(summary = "Stripe webhook (checkout.session.completed)")
    @PostMapping
    public ResponseEntity<Void> listenToStripe(@RequestBody String event,
                                               @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        paymentService.handleStripeWebhook(event, signature);
        return ResponseEntity.ok().build();
    }
}
