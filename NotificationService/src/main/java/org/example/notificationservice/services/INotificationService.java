package org.example.notificationservice.services;

import org.example.notificationservice.dtos.EmailDto;
import org.example.notificationservice.dtos.NotificationDto;
import org.example.notificationservice.dtos.SmsDto;

import java.util.List;

public interface INotificationService {
    NotificationDto sendEmail(EmailDto emailDto, String source);

    NotificationDto sendSms(SmsDto smsDto, String source);

    // Most recent notifications first
    List<NotificationDto> getRecentNotifications();
}
