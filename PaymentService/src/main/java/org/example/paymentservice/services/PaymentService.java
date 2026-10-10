package org.example.paymentservice.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.net.Webhook;
import lombok.extern.slf4j.Slf4j;
import org.example.paymentservice.clients.KafkaProducerClient;
import org.example.paymentservice.dtos.EmailDto;
import org.example.paymentservice.dtos.PaymentEventDto;
import org.example.paymentservice.dtos.ReceiptDto;
import org.example.paymentservice.dtos.SmsDto;
import org.example.paymentservice.dtos.UserDto;
import org.example.paymentservice.exceptions.ForbiddenException;
import org.example.paymentservice.exceptions.InvalidPaymentStateException;
import org.example.paymentservice.exceptions.InvalidSignatureException;
import org.example.paymentservice.exceptions.PaymentGatewayException;
import org.example.paymentservice.exceptions.PaymentNotFoundException;
import org.example.paymentservice.models.Payment;
import org.example.paymentservice.models.PaymentGatewayType;
import org.example.paymentservice.models.PaymentStatus;
import org.example.paymentservice.models.PaymentTransaction;
import org.example.paymentservice.paymentgateway.IPaymentGateway;
import org.example.paymentservice.paymentgateway.PaymentGatewayChooserStrategy;
import org.example.paymentservice.paymentgateway.PaymentLinkDto;
import org.example.paymentservice.paymentgateway.RazorpaySignatureVerifier;
import org.example.paymentservice.repos.PaymentRepo;
import org.example.paymentservice.repos.PaymentTransactionRepo;
import org.example.paymentservice.utils.mappers.PaymentDtoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class PaymentService implements IPaymentService {

    public static final String PAYMENT_EVENTS_TOPIC = "payment_events";
    public static final String PAYMENT_NOTIFICATIONS_TOPIC = "payment_notifications";
    public static final String SMS_NOTIFICATIONS_TOPIC = "sms_notifications";

    @Autowired
    private PaymentRepo paymentRepo;

    @Autowired
    private PaymentTransactionRepo paymentTransactionRepo;

    @Autowired
    private PaymentGatewayChooserStrategy paymentGatewayChooserStrategy;

    @Autowired
    private RazorpaySignatureVerifier razorpaySignatureVerifier;

    @Autowired
    private KafkaProducerClient kafkaProducerClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${stripe.webhook.secret:}")
    private String stripeWebhookSecret;

    @Value("${app.mail.from}")
    private String mailFrom;

    @Override
    public Payment initiatePayment(Long orderId, Long userId, Long amountInPaise, String currency,
                                   String customerName, String customerEmail, String customerPhone, String paymentMethod) {
        if (orderId == null || amountInPaise == null || amountInPaise <= 0) {
            throw new IllegalArgumentException("Order id and a positive amount are required");
        }

        // Kafka and the REST fallback can both ask for the same order, so reuse the open payment.
        Optional<Payment> optionalPayment = paymentRepo.findFirstByOrderIdAndStatusIn(orderId,
                List.of(PaymentStatus.PENDING, PaymentStatus.SUCCESS));
        if (optionalPayment.isPresent()) {
            Payment payment = optionalPayment.get();
            if (userId != null && !userId.equals(payment.getUserId())) {
                throw new ForbiddenException("Order " + orderId + " belongs to another user");
            }
            if (!payment.getAmount().equals(amountInPaise)) {
                throw new InvalidPaymentStateException("Order " + orderId + " already has a payment for a different amount");
            }
            if (payment.getStatus() == PaymentStatus.PENDING) {
                publishPaymentEvent(PaymentEventDto.LINK_CREATED, payment, null);
            }
            return payment;
        }

        IPaymentGateway gateway = paymentGatewayChooserStrategy.getBestPaymentGateway();
        Payment payment = new Payment();
        payment.setOrderId(orderId);
        payment.setUserId(userId);
        payment.setAmount(amountInPaise);
        String paymentCurrency = "INR";
        if (currency != null && !currency.isBlank()) {
            paymentCurrency = currency;
        }
        payment.setCurrency(paymentCurrency);
        payment.setCustomerName(customerName);
        payment.setCustomerEmail(customerEmail);
        payment.setCustomerPhone(normalizePhone(customerPhone));
        payment.setPaymentMethod(paymentMethod);
        payment.setGateway(gateway.getType());
        payment.setStatus(PaymentStatus.PENDING);
        payment = paymentRepo.save(payment);

        try {
            PaymentLinkDto paymentLinkDto = gateway.createPaymentLink(payment);
            payment.setPaymentLink(paymentLinkDto.getUrl());
            payment.setGatewayReferenceId(paymentLinkDto.getGatewayReferenceId());
            payment = paymentRepo.save(payment);
            logTransaction(payment, "LINK_CREATED", paymentLinkDto.getUrl());
            publishPaymentEvent(PaymentEventDto.LINK_CREATED, payment, null);
            return payment;
        } catch (PaymentGatewayException exception) {
            log.error("Could not create payment link for order {}: {}", orderId, exception.getMessage());
            markFailed(payment, exception.getMessage(), "LINK_FAILED");
            throw exception;
        }
    }

    @Override
    public Payment getPayment(Long paymentId, UserDto userDto) {
        Payment payment = findPayment(paymentId);
        if (!payment.getUserId().equals(userDto.getUserId()) && !userDto.isAdmin()) {
            throw new ForbiddenException("You can only view your own payments");
        }
        return payment;
    }

    @Override
    public List<Payment> getPaymentsForOrder(Long orderId, UserDto userDto) {
        List<Payment> visiblePayments = new ArrayList<>();
        List<Payment> payments = paymentRepo.findAllByOrderIdOrderByCreatedAtDesc(orderId);
        for (Payment payment : payments) {
            if (userDto.isAdmin() || payment.getUserId().equals(userDto.getUserId())) {
                visiblePayments.add(payment);
            }
        }
        return visiblePayments;
    }

    @Override
    public ReceiptDto getReceipt(Long paymentId, UserDto userDto) {
        Payment payment = getPayment(paymentId, userDto);
        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new InvalidPaymentStateException("Payment " + paymentId + " is " + payment.getStatus() + ", receipts exist only for successful payments");
        }
        return PaymentDtoMapper.toReceipt(payment);
    }

    @Override
    public Payment handleRazorpayCallback(Map<String, String> params) {
        if (!razorpaySignatureVerifier.isValidCallback(params)) {
            throw new InvalidSignatureException("Payment callback signature is invalid");
        }
        String paymentLinkId = params.get("razorpay_payment_link_id");
        Optional<Payment> optionalPayment = paymentRepo.findByGatewayReferenceId(paymentLinkId);
        if (optionalPayment.isEmpty()) {
            throw new PaymentNotFoundException("No payment for payment link " + paymentLinkId);
        }
        Payment payment = optionalPayment.get();
        logTransaction(payment, "CALLBACK", params.toString());

        if ("paid".equalsIgnoreCase(params.get("razorpay_payment_link_status"))) {
            return markSuccess(payment, params.get("razorpay_payment_id"));
        }
        return payment;
    }

    @Override
    public void handleRazorpayWebhook(String payload, String signature) {
        if (!razorpaySignatureVerifier.isValidWebhook(payload, signature)) {
            throw new InvalidSignatureException("Webhook signature is invalid");
        }
        JsonNode root = readJson(payload);
        String event = root.path("event").asText();
        String paymentLinkId = root.path("payload").path("payment_link").path("entity").path("id").asText(null);
        if (paymentLinkId == null) {
            log.info("Ignoring Razorpay webhook {} without a payment link", event);
            return;
        }
        Optional<Payment> optionalPayment = paymentRepo.findByGatewayReferenceId(paymentLinkId);
        if (optionalPayment.isEmpty()) {
            log.warn("Razorpay webhook {} for unknown payment link {}", event, paymentLinkId);
            return;
        }
        Payment payment = optionalPayment.get();
        logTransaction(payment, "WEBHOOK " + event, payload);

        if ("payment_link.paid".equals(event)) {
            String gatewayPaymentId = root.path("payload").path("payment").path("entity").path("id").asText(null);
            markSuccess(payment, gatewayPaymentId);
        } else if ("payment_link.expired".equals(event) || "payment_link.cancelled".equals(event)) {
            if (payment.getStatus() == PaymentStatus.PENDING) {
                markFailed(payment, "Payment link " + event.substring("payment_link.".length()), "FAILED");
            }
        } else {
            log.info("Ignoring Razorpay webhook event {}", event);
        }
    }

    @Override
    public void handleStripeWebhook(String payload, String signature) {
        if (stripeWebhookSecret == null || stripeWebhookSecret.isBlank() || signature == null) {
            throw new InvalidSignatureException("Stripe webhook secret is not configured or signature is missing");
        }
        try {
            Webhook.constructEvent(payload, signature, stripeWebhookSecret);
        } catch (SignatureVerificationException exception) {
            throw new InvalidSignatureException("Webhook signature is invalid");
        }

        JsonNode root = readJson(payload);
        String event = root.path("type").asText();
        if (!"checkout.session.completed".equals(event)) {
            log.info("Ignoring Stripe webhook event {}", event);
            return;
        }
        JsonNode session = root.path("data").path("object");
        String paymentLinkId = session.path("payment_link").asText(null);
        if (paymentLinkId == null) {
            return;
        }
        Optional<Payment> optionalPayment = paymentRepo.findByGatewayReferenceId(paymentLinkId);
        if (optionalPayment.isEmpty()) {
            return;
        }
        Payment payment = optionalPayment.get();
        logTransaction(payment, "WEBHOOK " + event, payload);
        if ("paid".equals(session.path("payment_status").asText())) {
            markSuccess(payment, session.path("payment_intent").asText(null));
        }
    }

    @Override
    public Payment completeMockPayment(Long paymentId, boolean successful) {
        Payment payment = findPayment(paymentId);
        if (payment.getGateway() != PaymentGatewayType.MOCK) {
            throw new InvalidPaymentStateException("Payment " + paymentId + " uses " + payment.getGateway() + ", not the mock gateway");
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            return payment;
        }
        String outcome = "failure";
        if (successful) {
            outcome = "success";
        }
        logTransaction(payment, "MOCK_CALLBACK", outcome);
        if (successful) {
            return markSuccess(payment, "mock_pay_" + payment.getId());
        } else {
            return markFailed(payment, "Payment declined", "FAILED");
        }
    }

    private Payment markSuccess(Payment payment, String gatewayPaymentId) {
        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            return payment;
        }
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setGatewayPaymentId(gatewayPaymentId);
        payment.setFailureReason(null);
        payment.setPaidAt(new Date());
        Payment saved = paymentRepo.save(payment);
        logTransaction(saved, "SUCCESS", gatewayPaymentId);
        publishPaymentEvent(PaymentEventDto.SUCCESS, saved, null);
        sendReceiptEmail(saved);
        sendReceiptSms(saved);
        return saved;
    }

    private Payment markFailed(Payment payment, String reason, String transactionType) {
        payment.setStatus(PaymentStatus.FAILED);
        payment.setFailureReason(reason);
        Payment saved = paymentRepo.save(payment);
        logTransaction(saved, transactionType, reason);
        publishPaymentEvent(PaymentEventDto.FAILED, saved, reason);
        return saved;
    }

    private Payment findPayment(Long paymentId) {
        Optional<Payment> optionalPayment = paymentRepo.findById(paymentId);
        if (optionalPayment.isEmpty()) {
            throw new PaymentNotFoundException("Payment " + paymentId + " not found");
        }
        return optionalPayment.get();
    }

    private void logTransaction(Payment payment, String eventType, String details) {
        String savedDetails = details;
        if (details != null && details.length() > 4000) {
            savedDetails = details.substring(0, 4000);
        }
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setPayment(payment);
        transaction.setEventType(eventType);
        transaction.setDetails(savedDetails);
        paymentTransactionRepo.save(transaction);
    }

    private void publishPaymentEvent(String type, Payment payment, String failureReason) {
        PaymentEventDto paymentEventDto = new PaymentEventDto();
        paymentEventDto.setType(type);
        paymentEventDto.setOrderId(payment.getOrderId());
        paymentEventDto.setPaymentId(payment.getId());
        paymentEventDto.setPaymentLink(payment.getPaymentLink());
        paymentEventDto.setAmountInPaise(payment.getAmount());
        paymentEventDto.setGatewayPaymentId(payment.getGatewayPaymentId());
        paymentEventDto.setFailureReason(failureReason);
        kafkaProducerClient.sendMessage(PAYMENT_EVENTS_TOPIC, toJson(paymentEventDto));
    }

    private void sendReceiptEmail(Payment payment) {
        if (payment.getCustomerEmail() == null) {
            return;
        }
        ReceiptDto receiptDto = PaymentDtoMapper.toReceipt(payment);
        String customerName = "there";
        if (payment.getCustomerName() != null) {
            customerName = payment.getCustomerName();
        }
        String paidVia = receiptDto.getGateway();
        if (receiptDto.getGatewayPaymentId() != null) {
            paidVia = paidVia + " (" + receiptDto.getGatewayPaymentId() + ")";
        }
        String body = "Hi " + customerName + ",\n\n"
                + "We received your payment. Here is your receipt.\n\n"
                + "Receipt number: " + receiptDto.getReceiptNumber() + "\n"
                + "Order: #" + receiptDto.getOrderId() + "\n"
                + "Amount: " + receiptDto.getCurrency() + " " + receiptDto.getAmount() + "\n"
                + "Paid via: " + paidVia + "\n"
                + "Paid at: " + receiptDto.getPaidAt() + "\n\n"
                + "Thank you for shopping with us.";
        EmailDto emailDto = new EmailDto();
        emailDto.setTo(payment.getCustomerEmail());
        emailDto.setFrom(mailFrom);
        emailDto.setSubject("Payment receipt " + receiptDto.getReceiptNumber() + " for order #" + payment.getOrderId());
        emailDto.setBody(body);
        kafkaProducerClient.sendMessage(PAYMENT_NOTIFICATIONS_TOPIC, toJson(emailDto));
    }

    private void sendReceiptSms(Payment payment) {
        if (payment.getCustomerPhone() == null) {
            return;
        }
        ReceiptDto receiptDto = PaymentDtoMapper.toReceipt(payment);
        SmsDto smsDto = new SmsDto();
        smsDto.setTo(payment.getCustomerPhone());
        smsDto.setMessage("Payment of " + receiptDto.getCurrency() + " " + receiptDto.getAmount() + " received for order #"
                + payment.getOrderId() + ". Receipt " + receiptDto.getReceiptNumber() + ".");
        kafkaProducerClient.sendMessage(SMS_NOTIFICATIONS_TOPIC, toJson(smsDto));
    }

    private String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String digits = phone.replaceAll("[^0-9+]", "");
        if (digits.isEmpty()) {
            return null;
        }
        return digits;
    }

    private JsonNode readJson(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Webhook payload is not valid JSON");
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new RuntimeException(exception);
        }
    }
}
