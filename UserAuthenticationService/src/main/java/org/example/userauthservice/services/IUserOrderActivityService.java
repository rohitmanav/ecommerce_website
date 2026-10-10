package org.example.userauthservice.services;

import org.example.userauthservice.dtos.OrderStatusEventDto;
import org.example.userauthservice.dtos.OrderSummaryDto;

public interface IUserOrderActivityService {
    void recordOrderStatus(OrderStatusEventDto orderStatusEventDto);

    OrderSummaryDto getOrderSummary(Long userId);
}
