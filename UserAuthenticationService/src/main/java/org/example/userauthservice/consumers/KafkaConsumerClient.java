package org.example.userauthservice.consumers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.userauthservice.dtos.OrderStatusEventDto;
import org.example.userauthservice.services.IUserOrderActivityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class KafkaConsumerClient {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IUserOrderActivityService userOrderActivityService;

    @KafkaListener(topics = "order_status_updates", groupId = "userService")
    public void onOrderStatusUpdate(String message) {
        try {
            OrderStatusEventDto orderStatusEventDto = objectMapper.readValue(message, OrderStatusEventDto.class);
            if (orderStatusEventDto.getOrderId() == null || orderStatusEventDto.getUserId() == null || orderStatusEventDto.getStatus() == null) {
                log.warn("Ignoring incomplete order status event: {}", message);
                return;
            }
            userOrderActivityService.recordOrderStatus(orderStatusEventDto);
        } catch (JsonProcessingException exception) {
            log.error("Could not read order status event: {}", exception.getMessage());
        }
    }
}
