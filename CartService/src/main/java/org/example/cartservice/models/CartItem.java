package org.example.cartservice.models;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class CartItem {
    private Long productId;
    private String productName;
    private String imageUrl;
    private Double unitPrice;
    private Integer quantity;
}
