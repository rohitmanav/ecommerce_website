package org.example.paymentservice.services;

import org.example.paymentservice.dtos.ReceiptDto;
import org.example.paymentservice.dtos.UserDto;
import org.example.paymentservice.models.Payment;

import java.util.List;
import java.util.Map;

public interface IPaymentService {
    Payment initiatePayment(Long orderId, Long userId, Long amountInPaise, String currency,
                            String customerName, String customerEmail, String customerPhone, String paymentMethod);

    Payment getPayment(Long paymentId, UserDto userDto);

    List<Payment> getPaymentsForOrder(Long orderId, UserDto userDto);

    ReceiptDto getReceipt(Long paymentId, UserDto userDto);

    Payment handleRazorpayCallback(Map<String, String> params);

    void handleRazorpayWebhook(String payload, String signature);

    void handleStripeWebhook(String payload, String signature);

    Payment completeMockPayment(Long paymentId, boolean successful);
}
