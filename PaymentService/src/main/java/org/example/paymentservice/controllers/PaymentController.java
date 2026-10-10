package org.example.paymentservice.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.paymentservice.clients.AuthClient;
import org.example.paymentservice.dtos.PaymentDto;
import org.example.paymentservice.dtos.PaymentRequestDto;
import org.example.paymentservice.dtos.ReceiptDto;
import org.example.paymentservice.dtos.UserDto;
import org.example.paymentservice.models.Payment;
import org.example.paymentservice.services.IPaymentService;
import org.example.paymentservice.utils.mappers.PaymentDtoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@Tag(name = "Payments", description = "Create payment links, check payment status and download receipts")
@RestController
@RequestMapping("/payments")
public class PaymentController {

    @Autowired
    private IPaymentService paymentService;

    @Autowired
    private AuthClient authClient;

    @Operation(summary = "Create a payment link for an order (normally triggered by checkout through Kafka)")
    @PostMapping
    public ResponseEntity<PaymentDto> initiatePayment(@Valid @RequestBody PaymentRequestDto paymentRequestDto,
                                                      @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        UserDto userDto = authClient.validateToken(authorization);
        String customerName = userDto.getName();
        if (paymentRequestDto.getName() != null) {
            customerName = paymentRequestDto.getName();
        }
        String customerEmail = userDto.getEmail();
        if (paymentRequestDto.getEmail() != null) {
            customerEmail = paymentRequestDto.getEmail();
        }
        Payment payment = paymentService.initiatePayment(paymentRequestDto.getOrderId(), userDto.getUserId(),
                paymentRequestDto.getAmount(), "INR", customerName, customerEmail,
                paymentRequestDto.getPhoneNumber(), paymentRequestDto.getPaymentMethod());
        return new ResponseEntity<>(PaymentDtoMapper.from(payment), HttpStatus.CREATED);
    }

    @Operation(summary = "Get a payment by id")
    @GetMapping("/{paymentId}")
    public PaymentDto getPayment(@PathVariable Long paymentId,
                                 @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        UserDto userDto = authClient.validateToken(authorization);
        return PaymentDtoMapper.from(paymentService.getPayment(paymentId, userDto));
    }

    @Operation(summary = "List the payment attempts for an order, newest first")
    @GetMapping("/order/{orderId}")
    public List<PaymentDto> getPaymentsForOrder(@PathVariable Long orderId,
                                                @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        UserDto userDto = authClient.validateToken(authorization);
        List<PaymentDto> response = new ArrayList<>();
        List<Payment> payments = paymentService.getPaymentsForOrder(orderId, userDto);
        for (Payment payment : payments) {
            response.add(PaymentDtoMapper.from(payment));
        }
        return response;
    }

    @Operation(summary = "Get the receipt for a successful payment")
    @GetMapping("/{paymentId}/receipt")
    public ReceiptDto getReceipt(@PathVariable Long paymentId,
                                 @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        UserDto userDto = authClient.validateToken(authorization);
        return paymentService.getReceipt(paymentId, userDto);
    }
}
