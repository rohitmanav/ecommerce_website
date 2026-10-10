package org.example.paymentservice.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.paymentservice.clients.KafkaProducerClient;
import org.example.paymentservice.dtos.UserDto;
import org.example.paymentservice.exceptions.ForbiddenException;
import org.example.paymentservice.exceptions.InvalidPaymentStateException;
import org.example.paymentservice.exceptions.InvalidSignatureException;
import org.example.paymentservice.exceptions.PaymentGatewayException;
import org.example.paymentservice.models.Payment;
import org.example.paymentservice.models.PaymentGatewayType;
import org.example.paymentservice.models.PaymentStatus;
import org.example.paymentservice.paymentgateway.IPaymentGateway;
import org.example.paymentservice.paymentgateway.PaymentGatewayChooserStrategy;
import org.example.paymentservice.paymentgateway.PaymentLinkDto;
import org.example.paymentservice.paymentgateway.RazorpaySignatureVerifier;
import org.example.paymentservice.repos.PaymentRepo;
import org.example.paymentservice.repos.PaymentTransactionRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepo paymentRepo;

    @Mock
    private PaymentTransactionRepo paymentTransactionRepo;

    @Mock
    private PaymentGatewayChooserStrategy paymentGatewayChooserStrategy;

    @Mock
    private RazorpaySignatureVerifier razorpaySignatureVerifier;

    @Mock
    private KafkaProducerClient kafkaProducerClient;

    @Mock
    private IPaymentGateway paymentGateway;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(paymentService, "mailFrom", "no-reply@test.local");
        ReflectionTestUtils.setField(paymentService, "stripeWebhookSecret", "");
    }

    @Test
    void initiatePaymentCreatesLinkAndPublishesLinkCreated() {
        stubSaveAssigningId();
        when(paymentRepo.findFirstByOrderIdAndStatusIn(eq(10L), anyCollection())).thenReturn(Optional.empty());
        when(paymentGatewayChooserStrategy.getBestPaymentGateway()).thenReturn(paymentGateway);
        when(paymentGateway.getType()).thenReturn(PaymentGatewayType.RAZORPAY);
        when(paymentGateway.createPaymentLink(any())).thenReturn(new PaymentLinkDto("https://rzp.io/i/abc", "plink_1"));

        Payment payment = paymentService.initiatePayment(10L, 1L, 49900L, "INR", "Asha", "asha@test.local", "+91 98765-43210", "UPI");

        assertEquals(PaymentStatus.PENDING, payment.getStatus());
        assertEquals("https://rzp.io/i/abc", payment.getPaymentLink());
        assertEquals("plink_1", payment.getGatewayReferenceId());
        assertEquals("+919876543210", payment.getCustomerPhone());
        verify(kafkaProducerClient).sendMessage(eq(PaymentService.PAYMENT_EVENTS_TOPIC),
                argThat(message -> message.contains("LINK_CREATED") && message.contains("https://rzp.io/i/abc")));
    }

    @Test
    void initiatePaymentReusesOpenPaymentForSameOrder() {
        Payment existing = payment(PaymentStatus.PENDING, PaymentGatewayType.RAZORPAY);
        when(paymentRepo.findFirstByOrderIdAndStatusIn(eq(10L), anyCollection())).thenReturn(Optional.of(existing));

        Payment payment = paymentService.initiatePayment(10L, 1L, 49900L, "INR", "Asha", "asha@test.local", null, null);

        assertSame(existing, payment);
        verifyNoInteractions(paymentGatewayChooserStrategy);
        verify(paymentRepo, never()).save(any());
    }

    @Test
    void gatewayFailureMarksPaymentFailedAndPublishesFailure() {
        stubSaveAssigningId();
        when(paymentRepo.findFirstByOrderIdAndStatusIn(eq(10L), anyCollection())).thenReturn(Optional.empty());
        when(paymentGatewayChooserStrategy.getBestPaymentGateway()).thenReturn(paymentGateway);
        when(paymentGateway.getType()).thenReturn(PaymentGatewayType.RAZORPAY);
        when(paymentGateway.createPaymentLink(any())).thenThrow(new PaymentGatewayException("Authentication failed"));

        assertThrows(PaymentGatewayException.class,
                () -> paymentService.initiatePayment(10L, 1L, 49900L, "INR", "Asha", "asha@test.local", null, null));

        verify(paymentRepo, atLeastOnce()).save(argThat(saved -> saved.getStatus() == PaymentStatus.FAILED));
        verify(kafkaProducerClient).sendMessage(eq(PaymentService.PAYMENT_EVENTS_TOPIC), argThat(message -> message.contains("FAILED")));
    }

    @Test
    void mockPaymentSuccessPublishesSuccessAndReceiptOnlyOnce() {
        Payment payment = payment(PaymentStatus.PENDING, PaymentGatewayType.MOCK);
        payment.setCustomerPhone("+919876543210");
        when(paymentRepo.findById(5L)).thenReturn(Optional.of(payment));
        when(paymentRepo.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        paymentService.completeMockPayment(5L, true);
        paymentService.completeMockPayment(5L, true);

        verify(kafkaProducerClient, times(1)).sendMessage(eq(PaymentService.SMS_NOTIFICATIONS_TOPIC),
                argThat(message -> message.contains("+919876543210") && message.contains("RCPT-00000005")));

        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
        assertNotNull(payment.getPaidAt());
        verify(kafkaProducerClient, times(1)).sendMessage(eq(PaymentService.PAYMENT_EVENTS_TOPIC),
                argThat(message -> message.contains("\"SUCCESS\"") && message.contains("49900")));
        verify(kafkaProducerClient, times(1)).sendMessage(eq(PaymentService.PAYMENT_NOTIFICATIONS_TOPIC),
                argThat(message -> message.contains("RCPT-00000005") && message.contains("499.00")));
    }

    @Test
    void mockPayRejectsRealGatewayPayments() {
        when(paymentRepo.findById(5L)).thenReturn(Optional.of(payment(PaymentStatus.PENDING, PaymentGatewayType.RAZORPAY)));

        assertThrows(InvalidPaymentStateException.class, () -> paymentService.completeMockPayment(5L, true));
    }

    @Test
    void razorpayCallbackWithBadSignatureIsRejected() {
        when(razorpaySignatureVerifier.isValidCallback(any())).thenReturn(false);

        assertThrows(InvalidSignatureException.class,
                () -> paymentService.handleRazorpayCallback(Map.of("razorpay_payment_link_status", "paid")));
        verify(paymentRepo, never()).save(any());
    }

    @Test
    void razorpayCallbackWithValidSignatureConfirmsPayment() {
        Payment payment = payment(PaymentStatus.PENDING, PaymentGatewayType.RAZORPAY);
        when(razorpaySignatureVerifier.isValidCallback(any())).thenReturn(true);
        when(paymentRepo.findByGatewayReferenceId("plink_1")).thenReturn(Optional.of(payment));
        when(paymentRepo.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        paymentService.handleRazorpayCallback(Map.of(
                "razorpay_payment_link_id", "plink_1",
                "razorpay_payment_link_status", "paid",
                "razorpay_payment_id", "pay_123"));

        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
        assertEquals("pay_123", payment.getGatewayPaymentId());
    }

    @Test
    void receiptIsOnlyAvailableToOwnerAfterSuccess() {
        when(paymentRepo.findById(5L)).thenReturn(Optional.of(payment(PaymentStatus.PENDING, PaymentGatewayType.MOCK)));

        assertThrows(ForbiddenException.class, () -> paymentService.getReceipt(5L, user(2L)));
        assertThrows(InvalidPaymentStateException.class, () -> paymentService.getReceipt(5L, user(1L)));
    }

    private void stubSaveAssigningId() {
        when(paymentRepo.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            if (payment.getId() == null) {
                payment.setId(5L);
            }
            return payment;
        });
    }

    private Payment payment(PaymentStatus status, PaymentGatewayType gateway) {
        Payment payment = new Payment();
        payment.setId(5L);
        payment.setOrderId(10L);
        payment.setUserId(1L);
        payment.setAmount(49900L);
        payment.setCustomerName("Asha");
        payment.setCustomerEmail("asha@test.local");
        payment.setGateway(gateway);
        payment.setStatus(status);
        payment.setGatewayReferenceId("plink_1");
        return payment;
    }

    private UserDto user(Long id) {
        UserDto userDto = new UserDto();
        userDto.setUserId(id);
        return userDto;
    }
}
