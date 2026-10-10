package org.example.cartservice.dtos;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class UpdateCartItemRequestDto {
    // 0 removes the item
    @NotNull
    @Min(0)
    private Integer quantity;
}
