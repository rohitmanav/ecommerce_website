package org.example.orderservice.models;

import java.util.EnumSet;
import java.util.Set;

public enum OrderStatus {
    PAYMENT_PENDING,
    PAYMENT_FAILED,
    CONFIRMED,
    PACKED,
    SHIPPED,
    OUT_FOR_DELIVERY,
    DELIVERED,
    CANCELLED;

    public static final Set<OrderStatus> CANCELLABLE = EnumSet.of(PAYMENT_PENDING, PAYMENT_FAILED, CONFIRMED, PACKED);

    // Payment statuses are set by the payment service, so admins can only move an unpaid order to CANCELLED.
    public boolean canMoveTo(OrderStatus next) {
        if (this == CONFIRMED) {
            return next == PACKED || next == CANCELLED;
        } else if (this == PACKED) {
            return next == SHIPPED || next == CANCELLED;
        } else if (this == SHIPPED) {
            return next == OUT_FOR_DELIVERY || next == DELIVERED;
        } else if (this == OUT_FOR_DELIVERY) {
            return next == DELIVERED;
        } else if (this == PAYMENT_PENDING || this == PAYMENT_FAILED) {
            return next == CANCELLED;
        } else {
            return false;
        }
    }
}
