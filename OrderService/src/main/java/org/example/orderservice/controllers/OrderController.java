package org.example.orderservice.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.orderservice.clients.AuthClient;
import org.example.orderservice.dtos.CheckoutRequestDto;
import org.example.orderservice.dtos.OrderDto;
import org.example.orderservice.dtos.OrderTrackingDto;
import org.example.orderservice.dtos.UpdateOrderStatusRequestDto;
import org.example.orderservice.dtos.UserDto;
import org.example.orderservice.exceptions.ForbiddenException;
import org.example.orderservice.models.Order;
import org.example.orderservice.services.IOrderService;
import org.example.orderservice.utils.mappers.OrderDtoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@Tag(name = "Orders", description = "Checkout, order history and order tracking")
@RestController
@RequestMapping("/orders")
public class OrderController {

    @Autowired
    private IOrderService orderService;

    @Autowired
    private AuthClient authClient;

    @Operation(summary = "Checkout the cart",
            description = "Creates the order from the cart, starts payment and returns the order with a payment link when it is ready")
    @PostMapping("/checkout")
    public ResponseEntity<OrderDto> checkout(@Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
                                             @Valid @RequestBody CheckoutRequestDto checkoutRequestDto) {
        UserDto userDto = authClient.validateToken(authorizationHeader);
        Order order = orderService.checkout(userDto, authorizationHeader,
                OrderDtoMapper.from(checkoutRequestDto.getDeliveryAddress()), checkoutRequestDto.getPaymentMethod());
        return new ResponseEntity<>(OrderDtoMapper.from(order), HttpStatus.CREATED);
    }

    @Operation(summary = "Order history of the logged-in user, newest first")
    @GetMapping
    public List<OrderDto> getMyOrders(@Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader) {
        UserDto userDto = authClient.validateToken(authorizationHeader);
        List<OrderDto> response = new ArrayList<>();
        List<Order> orders = orderService.getOrdersForUser(userDto.getUserId());
        for (Order order : orders) {
            response.add(OrderDtoMapper.from(order));
        }
        return response;
    }

    @Operation(summary = "Get an order", description = "Also returns the payment link while payment is pending")
    @GetMapping("/{id}")
    public OrderDto getOrder(@Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
                             @PathVariable Long id) {
        UserDto userDto = authClient.validateToken(authorizationHeader);
        return OrderDtoMapper.from(orderService.getOrder(id, userDto));
    }

    @Operation(summary = "Track the delivery status of an order")
    @GetMapping("/{id}/tracking")
    public OrderTrackingDto trackOrder(@Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
                                       @PathVariable Long id) {
        UserDto userDto = authClient.validateToken(authorizationHeader);
        return OrderDtoMapper.toTracking(orderService.getOrder(id, userDto));
    }

    @Operation(summary = "Cancel an order that has not shipped yet")
    @PostMapping("/{id}/cancel")
    public OrderDto cancelOrder(@Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
                                @PathVariable Long id) {
        UserDto userDto = authClient.validateToken(authorizationHeader);
        return OrderDtoMapper.from(orderService.cancelOrder(id, userDto));
    }

    @Operation(summary = "Move an order to its next delivery status (admin only)",
            description = "CONFIRMED -> PACKED -> SHIPPED -> OUT_FOR_DELIVERY -> DELIVERED, or CANCELLED before shipping")
    @PutMapping("/{id}/status")
    public OrderDto updateStatus(@Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
                                 @PathVariable Long id,
                                 @Valid @RequestBody UpdateOrderStatusRequestDto updateOrderStatusRequestDto) {
        UserDto userDto = authClient.validateToken(authorizationHeader);
        if (!userDto.isAdmin()) {
            throw new ForbiddenException("Only admins can update the delivery status");
        }
        return OrderDtoMapper.from(orderService.updateStatus(id,
                updateOrderStatusRequestDto.getStatus(), updateOrderStatusRequestDto.getNote()));
    }
}
