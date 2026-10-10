package org.example.orderservice.utils.mappers;

import org.example.orderservice.dtos.AddressDto;
import org.example.orderservice.dtos.OrderDto;
import org.example.orderservice.dtos.OrderItemDto;
import org.example.orderservice.dtos.OrderTrackingDto;
import org.example.orderservice.models.Address;
import org.example.orderservice.models.Order;
import org.example.orderservice.models.OrderItem;
import org.example.orderservice.models.OrderStatusHistory;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class OrderDtoMapper {

    public static OrderDto from(Order order) {
        OrderDto orderDto = new OrderDto();
        orderDto.setId(order.getId());
        orderDto.setUserId(order.getUserId());
        if (order.getStatus() != null) {
            orderDto.setStatus(order.getStatus().name());
        }
        orderDto.setTotalAmount(order.getTotalAmount());
        orderDto.setCurrency(order.getCurrency());
        if (order.getPaymentMethod() != null) {
            orderDto.setPaymentMethod(order.getPaymentMethod().name());
        }
        orderDto.setPaymentId(order.getPaymentId());
        orderDto.setPaymentLink(order.getPaymentLink());
        orderDto.setCreatedAt(order.getCreatedAt());
        orderDto.setLastUpdatedAt(order.getLastUpdatedAt());
        if (order.getDeliveryAddress() != null) {
            orderDto.setDeliveryAddress(from(order.getDeliveryAddress()));
        }
        for (OrderItem item : order.getItems()) {
            OrderItemDto orderItemDto = new OrderItemDto();
            orderItemDto.setProductId(item.getProductId());
            orderItemDto.setProductName(item.getProductName());
            orderItemDto.setImageUrl(item.getImageUrl());
            orderItemDto.setUnitPrice(item.getUnitPrice());
            orderItemDto.setQuantity(item.getQuantity());
            orderItemDto.setSubtotal(BigDecimal.valueOf(item.getUnitPrice())
                    .multiply(BigDecimal.valueOf(item.getQuantity()))
                    .setScale(2, RoundingMode.HALF_UP).doubleValue());
            orderDto.getItems().add(orderItemDto);
        }
        return orderDto;
    }

    public static OrderTrackingDto toTracking(Order order) {
        OrderTrackingDto orderTrackingDto = new OrderTrackingDto();
        orderTrackingDto.setOrderId(order.getId());
        orderTrackingDto.setCurrentStatus(order.getStatus().name());
        for (OrderStatusHistory history : order.getStatusHistory()) {
            OrderTrackingDto.StatusUpdateDto statusUpdateDto = new OrderTrackingDto.StatusUpdateDto();
            statusUpdateDto.setStatus(history.getStatus().name());
            statusUpdateDto.setNote(history.getNote());
            statusUpdateDto.setChangedAt(history.getChangedAt());
            orderTrackingDto.getHistory().add(statusUpdateDto);
        }
        return orderTrackingDto;
    }

    public static AddressDto from(Address address) {
        AddressDto addressDto = new AddressDto();
        addressDto.setFullName(address.getFullName());
        addressDto.setPhoneNumber(address.getPhoneNumber());
        addressDto.setLine1(address.getLine1());
        addressDto.setLine2(address.getLine2());
        addressDto.setCity(address.getCity());
        addressDto.setState(address.getState());
        addressDto.setPostalCode(address.getPostalCode());
        addressDto.setCountry(address.getCountry());
        return addressDto;
    }

    public static Address from(AddressDto addressDto) {
        Address address = new Address();
        address.setFullName(addressDto.getFullName());
        address.setPhoneNumber(addressDto.getPhoneNumber());
        address.setLine1(addressDto.getLine1());
        address.setLine2(addressDto.getLine2());
        address.setCity(addressDto.getCity());
        address.setState(addressDto.getState());
        address.setPostalCode(addressDto.getPostalCode());
        address.setCountry(addressDto.getCountry());
        return address;
    }
}
