package org.example.notificationservice.consumers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.notificationservice.dtos.EmailDto;
import org.example.notificationservice.dtos.SmsDto;
import org.example.notificationservice.services.INotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class KafkaConsumerClient {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private INotificationService notificationService;

    @KafkaListener(topics = {"signup", "password_reset", "order_notifications", "payment_notifications"},
            groupId = "notificationService")
    public void sendEmail(String message, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            EmailDto emailDto = objectMapper.readValue(message, EmailDto.class);
            if (emailDto.getTo() == null || emailDto.getTo().isBlank()) {
                log.warn("Ignoring {} message without a recipient", topic);
                return;
            }
            notificationService.sendEmail(emailDto, topic);
        } catch (JsonProcessingException exception) {
            log.error("Could not read {} message: {}", topic, exception.getMessage());
        }
    }

    @KafkaListener(topics = "sms_notifications", groupId = "notificationService")
    public void sendSms(String message) {
        try {
            SmsDto smsDto = objectMapper.readValue(message, SmsDto.class);
            if (smsDto.getTo() == null || smsDto.getTo().isBlank() || smsDto.getMessage() == null) {
                log.warn("Ignoring SMS message without a recipient or text");
                return;
            }
            notificationService.sendSms(smsDto, "sms_notifications");
        } catch (JsonProcessingException exception) {
            log.error("Could not read SMS message: {}", exception.getMessage());
        }
    }
}
