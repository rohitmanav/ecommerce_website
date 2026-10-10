package org.example.userauthservice.services;

import org.example.userauthservice.dtos.OrderStatusEventDto;
import org.example.userauthservice.dtos.OrderSummaryDto;
import org.example.userauthservice.models.UserOrderActivity;
import org.example.userauthservice.repos.UserOrderActivityRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class UserOrderActivityService implements IUserOrderActivityService {

    private static final Set<String> PAID_STATUSES = Set.of("CONFIRMED", "PACKED", "SHIPPED", "OUT_FOR_DELIVERY", "DELIVERED");

    @Autowired
    private UserOrderActivityRepo userOrderActivityRepo;

    @Override
    public void recordOrderStatus(OrderStatusEventDto orderStatusEventDto) {
        Date occurredAt = orderStatusEventDto.getOccurredAt() != null ? orderStatusEventDto.getOccurredAt() : new Date();
        Optional<UserOrderActivity> optionalUserOrderActivity = userOrderActivityRepo.findByOrderId(orderStatusEventDto.getOrderId());
        UserOrderActivity activity;
        if (optionalUserOrderActivity.isEmpty()) {
            activity = new UserOrderActivity();
            activity.setOrderId(orderStatusEventDto.getOrderId());
        } else {
            activity = optionalUserOrderActivity.get();
            if (activity.getStatusChangedAt() != null && activity.getStatusChangedAt().after(occurredAt)) {
                // Kafka redelivered an older event; keep the newer status.
                return;
            }
        }
        activity.setUserId(orderStatusEventDto.getUserId());
        activity.setOrderStatus(orderStatusEventDto.getStatus());
        activity.setTotalAmount(orderStatusEventDto.getTotalAmount());
        activity.setCurrency(orderStatusEventDto.getCurrency());
        activity.setStatusChangedAt(occurredAt);
        userOrderActivityRepo.save(activity);
    }

    @Override
    public OrderSummaryDto getOrderSummary(Long userId) {
        List<UserOrderActivity> activities = userOrderActivityRepo.findAllByUserIdOrderByStatusChangedAtDesc(userId);
        OrderSummaryDto orderSummaryDto = new OrderSummaryDto();
        BigDecimal totalSpent = BigDecimal.ZERO;
        for (UserOrderActivity activity : activities) {
            if (PAID_STATUSES.contains(activity.getOrderStatus())) {
                orderSummaryDto.setTotalOrders(orderSummaryDto.getTotalOrders() + 1);
                if (activity.getTotalAmount() != null) {
                    totalSpent = totalSpent.add(BigDecimal.valueOf(activity.getTotalAmount()));
                }
            }
            if ("DELIVERED".equals(activity.getOrderStatus())) {
                orderSummaryDto.setDeliveredOrders(orderSummaryDto.getDeliveredOrders() + 1);
            }
        }
        orderSummaryDto.setTotalSpent(totalSpent.setScale(2, RoundingMode.HALF_UP).doubleValue());
        if (!activities.isEmpty()) {
            UserOrderActivity latest = activities.get(0);
            orderSummaryDto.setLastOrderId(latest.getOrderId());
            orderSummaryDto.setLastOrderStatus(latest.getOrderStatus());
            orderSummaryDto.setLastOrderAt(latest.getStatusChangedAt());
        }
        return orderSummaryDto;
    }
}
