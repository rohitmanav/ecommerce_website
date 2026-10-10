package org.example.userauthservice.services;

import org.example.userauthservice.dtos.OrderStatusEventDto;
import org.example.userauthservice.dtos.OrderSummaryDto;
import org.example.userauthservice.models.UserOrderActivity;
import org.example.userauthservice.repos.UserOrderActivityRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserOrderActivityServiceTest {

    @Mock
    private UserOrderActivityRepo userOrderActivityRepo;

    @InjectMocks
    private UserOrderActivityService userOrderActivityService;

    @Test
    void newOrderStatusIsStored() {
        when(userOrderActivityRepo.findByOrderId(7L)).thenReturn(Optional.empty());

        userOrderActivityService.recordOrderStatus(event(7L, "CONFIRMED", new Date()));

        verify(userOrderActivityRepo).save(argThat(activity ->
                activity.getOrderId().equals(7L) && "CONFIRMED".equals(activity.getOrderStatus())));
    }

    @Test
    void olderRedeliveredEventDoesNotOverwriteNewerStatus() {
        UserOrderActivity existing = activity(7L, "SHIPPED", 500.0, new Date(2_000));
        when(userOrderActivityRepo.findByOrderId(7L)).thenReturn(Optional.of(existing));

        userOrderActivityService.recordOrderStatus(event(7L, "CONFIRMED", new Date(1_000)));

        assertEquals("SHIPPED", existing.getOrderStatus());
        verify(userOrderActivityRepo, never()).save(any());
    }

    @Test
    void summaryCountsOnlyPaidOrders() {
        when(userOrderActivityRepo.findAllByUserIdOrderByStatusChangedAtDesc(1L)).thenReturn(List.of(
                activity(9L, "PAYMENT_PENDING", 50.0, new Date(4_000)),
                activity(8L, "DELIVERED", 200.0, new Date(3_000)),
                activity(7L, "SHIPPED", 100.5, new Date(2_000)),
                activity(6L, "CANCELLED", 999.0, new Date(1_000))));

        OrderSummaryDto orderSummaryDto = userOrderActivityService.getOrderSummary(1L);

        assertEquals(2, orderSummaryDto.getTotalOrders());
        assertEquals(1, orderSummaryDto.getDeliveredOrders());
        assertEquals(300.5, orderSummaryDto.getTotalSpent());
        assertEquals(9L, orderSummaryDto.getLastOrderId());
        assertEquals("PAYMENT_PENDING", orderSummaryDto.getLastOrderStatus());
    }

    private OrderStatusEventDto event(Long orderId, String status, Date occurredAt) {
        OrderStatusEventDto orderStatusEventDto = new OrderStatusEventDto();
        orderStatusEventDto.setOrderId(orderId);
        orderStatusEventDto.setUserId(1L);
        orderStatusEventDto.setStatus(status);
        orderStatusEventDto.setTotalAmount(500.0);
        orderStatusEventDto.setCurrency("INR");
        orderStatusEventDto.setOccurredAt(occurredAt);
        return orderStatusEventDto;
    }

    private UserOrderActivity activity(Long orderId, String status, Double amount, Date changedAt) {
        UserOrderActivity activity = new UserOrderActivity();
        activity.setOrderId(orderId);
        activity.setUserId(1L);
        activity.setOrderStatus(status);
        activity.setTotalAmount(amount);
        activity.setStatusChangedAt(changedAt);
        return activity;
    }
}
