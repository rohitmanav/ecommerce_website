package org.example.orderservice.clients;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class KafkaProducerClient {

    @Autowired
    KafkaTemplate<String,String> kafkaTemplate;

    // Failures are logged instead of thrown so order changes still succeed when Kafka is down.
    public void sendMessage(String topic,String message) {
        try {
            kafkaTemplate.send(topic, message).whenComplete((result, exception) -> {
                if (exception != null) {
                    log.warn("Could not publish message to topic {}: {}", topic, exception.getMessage());
                }
            });
        } catch (Exception exception) {
            log.warn("Could not publish message to topic {}: {}", topic, exception.getMessage());
        }
    }

    public boolean sendMessageAndWait(String topic, String message) {
        try {
            kafkaTemplate.send(topic, message).get(5, TimeUnit.SECONDS);
            return true;
        } catch (Exception exception) {
            log.warn("Could not publish message to topic {}: {}", topic, exception.getMessage());
            return false;
        }
    }

}
