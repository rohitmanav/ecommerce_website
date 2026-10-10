package org.example.paymentservice.paymentgateway;

import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class RazorpaySignatureVerifier {

    @Value("${razorpay.key.secret}")
    private String razorpaySecret;

    @Value("${razorpay.webhook.secret}")
    private String webhookSecret;

    public boolean isValidCallback(Map<String, String> params) {
        if (razorpaySecret == null || razorpaySecret.isBlank()) {
            return false;
        }
        try {
            JSONObject attributes = new JSONObject();
            attributes.put("payment_link_id", params.get("razorpay_payment_link_id"));
            attributes.put("payment_link_reference_id", params.get("razorpay_payment_link_reference_id"));
            attributes.put("payment_link_status", params.get("razorpay_payment_link_status"));
            attributes.put("razorpay_payment_id", params.get("razorpay_payment_id"));
            attributes.put("razorpay_signature", params.get("razorpay_signature"));
            return Utils.verifyPaymentLink(attributes, razorpaySecret);
        } catch (RazorpayException | RuntimeException exception) {
            log.warn("Could not verify Razorpay callback signature: {}", exception.getMessage());
            return false;
        }
    }

    public boolean isValidWebhook(String payload, String signature) {
        if (webhookSecret == null || webhookSecret.isBlank() || signature == null) {
            return false;
        }
        try {
            return Utils.verifyWebhookSignature(payload, signature, webhookSecret);
        } catch (RazorpayException | RuntimeException exception) {
            log.warn("Could not verify Razorpay webhook signature: {}", exception.getMessage());
            return false;
        }
    }
}
