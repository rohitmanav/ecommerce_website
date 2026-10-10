package org.example.paymentservice.dtos;

import lombok.Getter;
import lombok.Setter;

// Published on the sms_notifications topic; consumed by the notification service.
@Setter
@Getter
public class SmsDto {
    private String to;
    private String message;
}
