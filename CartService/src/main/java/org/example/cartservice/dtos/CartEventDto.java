package org.example.cartservice.dtos;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Setter
@Getter
public class CartEventDto {
    private Long userId;
    private Long productId;
    private Integer quantity;
    // ITEM_ADDED, ITEM_UPDATED, ITEM_REMOVED or CART_CLEARED
    private String action;
    private Date timestamp = new Date();
}
