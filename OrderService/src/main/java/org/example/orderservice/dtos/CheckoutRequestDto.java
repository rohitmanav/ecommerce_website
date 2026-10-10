package org.example.orderservice.dtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.example.orderservice.models.PaymentMethod;

@Setter
@Getter
public class CheckoutRequestDto {
    @NotNull
    @Valid
    private AddressDto deliveryAddress;

    @NotNull
    private PaymentMethod paymentMethod;
}
