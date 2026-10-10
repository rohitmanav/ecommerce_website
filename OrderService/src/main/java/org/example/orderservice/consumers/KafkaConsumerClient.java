package org.example.orderservice.consumers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.orderservice.dtos.PaymentEventDto;
import org.example.orderservice.services.IOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class KafkaConsumerClient {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IOrderService orderService;

    @KafkaListener(topics = "payment_events", groupId = "orderService")
    public void onPaymentEvent(String message) {
        try {
            PaymentEventDto paymentEventDto = objectMapper.readValue(message, PaymentEventDto.class);
            if (paymentEventDto.getOrderId() == null || paymentEventDto.getType() == null) {
                log.warn("Ignoring incomplete payment event: {}", message);
                return;
            }
            orderService.handlePaymentEvent(paymentEventDto);
        } catch (JsonProcessingException exception) {
            log.error("Could not read payment event: {}", exception.getMessage());
        }
    }
}
