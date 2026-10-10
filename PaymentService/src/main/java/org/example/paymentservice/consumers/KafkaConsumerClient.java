package org.example.paymentservice.consumers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.paymentservice.dtos.OrderCreatedEventDto;
import org.example.paymentservice.services.IPaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class KafkaConsumerClient {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IPaymentService paymentService;

    @KafkaListener(topics = "order_created", groupId = "paymentService")
    public void onOrderCreated(String message) {
        try {
            OrderCreatedEventDto orderCreatedEventDto = objectMapper.readValue(message, OrderCreatedEventDto.class);
            if (orderCreatedEventDto.getOrderId() == null || orderCreatedEventDto.getAmountInPaise() == null) {
                log.warn("Ignoring incomplete order_created event: {}", message);
                return;
            }
            paymentService.initiatePayment(orderCreatedEventDto.getOrderId(), orderCreatedEventDto.getUserId(),
                    orderCreatedEventDto.getAmountInPaise(), orderCreatedEventDto.getCurrency(),
                    orderCreatedEventDto.getUserName(), orderCreatedEventDto.getUserEmail(),
                    orderCreatedEventDto.getPhoneNumber(), orderCreatedEventDto.getPaymentMethod());
        } catch (JsonProcessingException exception) {
            log.error("Could not read order_created event: {}", exception.getMessage());
        } catch (RuntimeException exception) {
            // The failure is already recorded and published as a FAILED payment event.
            log.error("Could not start payment for order event {}: {}", message, exception.getMessage());
        }
    }
}
