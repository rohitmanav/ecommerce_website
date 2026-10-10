package org.example.paymentservice.utils.mappers;

import org.example.paymentservice.dtos.PaymentDto;
import org.example.paymentservice.dtos.ReceiptDto;
import org.example.paymentservice.models.Payment;

import java.math.BigDecimal;

public class PaymentDtoMapper {

    public static PaymentDto from(Payment payment) {
        PaymentDto paymentDto = new PaymentDto();
        paymentDto.setId(payment.getId());
        paymentDto.setOrderId(payment.getOrderId());
        paymentDto.setAmount(payment.getAmount());
        paymentDto.setCurrency(payment.getCurrency());
        if (payment.getStatus() != null) {
            paymentDto.setStatus(payment.getStatus().name());
        }
        if (payment.getGateway() != null) {
            paymentDto.setGateway(payment.getGateway().name());
        }
        paymentDto.setPaymentMethod(payment.getPaymentMethod());
        paymentDto.setPaymentLink(payment.getPaymentLink());
        paymentDto.setGatewayPaymentId(payment.getGatewayPaymentId());
        paymentDto.setFailureReason(payment.getFailureReason());
        paymentDto.setCreatedAt(payment.getCreatedAt());
        paymentDto.setPaidAt(payment.getPaidAt());
        return paymentDto;
    }

    public static ReceiptDto toReceipt(Payment payment) {
        ReceiptDto receiptDto = new ReceiptDto();
        receiptDto.setReceiptNumber(String.format("RCPT-%08d", payment.getId()));
        receiptDto.setPaymentId(payment.getId());
        receiptDto.setOrderId(payment.getOrderId());
        receiptDto.setCustomerName(payment.getCustomerName());
        receiptDto.setCustomerEmail(payment.getCustomerEmail());
        receiptDto.setAmount(BigDecimal.valueOf(payment.getAmount(), 2));
        receiptDto.setCurrency(payment.getCurrency());
        if (payment.getGateway() != null) {
            receiptDto.setGateway(payment.getGateway().name());
        }
        receiptDto.setPaymentMethod(payment.getPaymentMethod());
        receiptDto.setGatewayPaymentId(payment.getGatewayPaymentId());
        receiptDto.setPaidAt(payment.getPaidAt());
        return receiptDto;
    }
}
