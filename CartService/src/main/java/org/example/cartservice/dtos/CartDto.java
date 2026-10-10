package org.example.cartservice.dtos;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
public class CartDto {
    private Long userId;
    private List<CartItemDto> items = new ArrayList<>();
    private Integer totalItems;
    private Double totalAmount;
}
