package org.example.cartservice.utils.mappers;

import org.example.cartservice.dtos.CartDto;
import org.example.cartservice.dtos.CartItemDto;
import org.example.cartservice.models.Cart;
import org.example.cartservice.models.CartItem;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class CartDtoMapper {

    public static CartDto from(Cart cart) {
        CartDto cartDto = new CartDto();
        cartDto.setUserId(cart.getUserId());

        int totalItems = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (CartItem item : cart.getItems()) {
            CartItemDto cartItemDto = new CartItemDto();
            cartItemDto.setProductId(item.getProductId());
            cartItemDto.setProductName(item.getProductName());
            cartItemDto.setImageUrl(item.getImageUrl());
            cartItemDto.setUnitPrice(item.getUnitPrice());
            cartItemDto.setQuantity(item.getQuantity());
            BigDecimal subtotal = BigDecimal.valueOf(item.getUnitPrice()).multiply(BigDecimal.valueOf(item.getQuantity()));
            cartItemDto.setSubtotal(round(subtotal));
            cartDto.getItems().add(cartItemDto);

            totalItems += item.getQuantity();
            totalAmount = totalAmount.add(subtotal);
        }
        cartDto.setTotalItems(totalItems);
        cartDto.setTotalAmount(round(totalAmount));
        return cartDto;
    }

    private static Double round(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
