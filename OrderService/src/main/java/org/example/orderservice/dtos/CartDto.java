package org.example.orderservice.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CartDto {
    private Long userId;
    private List<CartItemDto> items = new ArrayList<>();
    private Double totalAmount;

    @Setter
    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CartItemDto {
        private Long productId;
        private String productName;
        private String imageUrl;
        private Double unitPrice;
        private Integer quantity;
    }
}
