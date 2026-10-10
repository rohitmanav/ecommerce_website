package org.example.cartservice.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.cartservice.clients.AuthClient;
import org.example.cartservice.dtos.AddToCartRequestDto;
import org.example.cartservice.dtos.CartDto;
import org.example.cartservice.dtos.UpdateCartItemRequestDto;
import org.example.cartservice.dtos.UserDto;
import org.example.cartservice.services.ICartService;
import org.example.cartservice.utils.mappers.CartDtoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Cart", description = "Add products, review and update the cart of the logged-in user")
@RestController
@RequestMapping("/cart")
public class CartController {

    @Autowired
    private ICartService cartService;

    @Autowired
    private AuthClient authClient;

    @Operation(summary = "Review the cart with prices, quantities and totals")
    @GetMapping
    public CartDto getCart(@Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader) {
        UserDto userDto = authClient.validateToken(authorizationHeader);
        return CartDtoMapper.from(cartService.getCart(userDto.getUserId()));
    }

    @Operation(summary = "Add a product to the cart", description = "Adding a product already in the cart increases its quantity")
    @PostMapping("/items")
    public CartDto addItem(@Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
                           @Valid @RequestBody AddToCartRequestDto addToCartRequestDto) {
        UserDto userDto = authClient.validateToken(authorizationHeader);
        return CartDtoMapper.from(cartService.addItem(userDto.getUserId(),
                addToCartRequestDto.getProductId(), addToCartRequestDto.getQuantity()));
    }

    @Operation(summary = "Change the quantity of a product in the cart", description = "Quantity 0 removes the product")
    @PutMapping("/items/{productId}")
    public CartDto updateItem(@Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
                              @PathVariable Long productId,
                              @Valid @RequestBody UpdateCartItemRequestDto updateCartItemRequestDto) {
        UserDto userDto = authClient.validateToken(authorizationHeader);
        return CartDtoMapper.from(cartService.updateItemQuantity(userDto.getUserId(), productId,
                updateCartItemRequestDto.getQuantity()));
    }

    @Operation(summary = "Remove a product from the cart")
    @DeleteMapping("/items/{productId}")
    public CartDto removeItem(@Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
                              @PathVariable Long productId) {
        UserDto userDto = authClient.validateToken(authorizationHeader);
        return CartDtoMapper.from(cartService.removeItem(userDto.getUserId(), productId));
    }

    @Operation(summary = "Empty the cart")
    @DeleteMapping
    public ResponseEntity<Void> clearCart(@Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader) {
        UserDto userDto = authClient.validateToken(authorizationHeader);
        cartService.clearCart(userDto.getUserId());
        return ResponseEntity.noContent().build();
    }
}
