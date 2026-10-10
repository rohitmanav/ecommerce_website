package org.example.notificationservice.dtos;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Setter
@Getter
public class NotificationDto {
    // EMAIL or SMS
    private String channel;
    private String source;
    private String to;
    private String subject;
    private String body;
    // SENT, LOGGED (no provider configured) or FAILED
    private String status;
    private String error;
    private Date createdAt = new Date();
}
