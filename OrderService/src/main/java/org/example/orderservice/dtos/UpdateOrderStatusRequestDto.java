package org.example.orderservice.dtos;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.example.orderservice.models.OrderStatus;

@Setter
@Getter
public class UpdateOrderStatusRequestDto {
    @NotNull
    private OrderStatus status;

    private String note;
}
