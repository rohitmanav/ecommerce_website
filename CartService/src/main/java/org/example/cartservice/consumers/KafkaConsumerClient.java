package org.example.cartservice.consumers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.cartservice.dtos.OrderCreatedEventDto;
import org.example.cartservice.services.ICartService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class KafkaConsumerClient {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ICartService cartService;

    @KafkaListener(topics = "order_created", groupId = "cartService")
    public void clearCartAfterOrder(String message) {
        try {
            OrderCreatedEventDto orderCreatedEventDto = objectMapper.readValue(message, OrderCreatedEventDto.class);
            if (orderCreatedEventDto.getUserId() == null) {
                log.warn("Ignoring order_created event without userId: {}", message);
                return;
            }
            cartService.clearCart(orderCreatedEventDto.getUserId());
            log.info("Cleared cart of user {} after order {}", orderCreatedEventDto.getUserId(), orderCreatedEventDto.getOrderId());
        } catch (JsonProcessingException exception) {
            log.error("Could not read order_created event: {}", exception.getMessage());
        }
    }
}
