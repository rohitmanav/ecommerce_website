package org.example.paymentservice.dtos;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class PaymentRequestDto {
    // Amount in paise.
    @NotNull
    @Min(100)
    private Long amount;

    @NotNull
    private Long orderId;

    private String phoneNumber;
    private String name;
    private String email;
    private String paymentMethod;
}
