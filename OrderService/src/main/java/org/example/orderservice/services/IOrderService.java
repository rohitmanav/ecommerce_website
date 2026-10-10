package org.example.orderservice.services;

import org.example.orderservice.dtos.PaymentEventDto;
import org.example.orderservice.dtos.UserDto;
import org.example.orderservice.models.Address;
import org.example.orderservice.models.Order;
import org.example.orderservice.models.OrderStatus;
import org.example.orderservice.models.PaymentMethod;

import java.util.List;

public interface IOrderService {

    Order checkout(UserDto userDto, String authorizationHeader, Address deliveryAddress, PaymentMethod paymentMethod);

    List<Order> getOrdersForUser(Long userId);

    Order getOrder(Long orderId, UserDto userDto);

    Order cancelOrder(Long orderId, UserDto userDto);

    Order updateStatus(Long orderId, OrderStatus newStatus, String note);

    void handlePaymentEvent(PaymentEventDto paymentEventDto);
}
