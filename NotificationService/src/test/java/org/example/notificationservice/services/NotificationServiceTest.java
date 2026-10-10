package org.example.notificationservice.services;

import org.example.notificationservice.dtos.EmailDto;
import org.example.notificationservice.dtos.NotificationDto;
import org.example.notificationservice.dtos.SmsDto;
import org.example.notificationservice.smsgateway.LogSmsSender;
import org.example.notificationservice.smsgateway.TwilioSmsSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class NotificationServiceTest {

    private NotificationService notificationService;

    private TwilioSmsSender twilioSmsSender;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService();
        twilioSmsSender = mock(TwilioSmsSender.class);
        ReflectionTestUtils.setField(notificationService, "smtpUsername", "");
        ReflectionTestUtils.setField(notificationService, "smtpPassword", "");
        ReflectionTestUtils.setField(notificationService, "historySize", 2);
        ReflectionTestUtils.setField(notificationService, "smsProvider", "log");
        ReflectionTestUtils.setField(notificationService, "defaultCountryCode", "+91");
        ReflectionTestUtils.setField(notificationService, "logSmsSender", new LogSmsSender());
        ReflectionTestUtils.setField(notificationService, "twilioSmsSender", twilioSmsSender);
    }

    @Test
    void emailIsLoggedWhenSmtpIsNotConfigured() {
        NotificationDto notificationDto = notificationService.sendEmail(email("a@test.local"), "signup");

        assertEquals("LOGGED", notificationDto.getStatus());
        assertEquals("EMAIL", notificationDto.getChannel());
        assertEquals("signup", notificationDto.getSource());
    }

    @Test
    void historyKeepsOnlyTheNewestNotifications() {
        notificationService.sendEmail(email("first@test.local"), "signup");
        notificationService.sendEmail(email("second@test.local"), "signup");
        notificationService.sendEmail(email("third@test.local"), "signup");

        List<NotificationDto> recent = notificationService.getRecentNotifications();
        assertEquals(2, recent.size());
        assertEquals("third@test.local", recent.get(0).getTo());
        assertEquals("second@test.local", recent.get(1).getTo());
    }

    @Test
    void smsIsLoggedWithInternationalNumberByDefault() {
        NotificationDto notificationDto = notificationService.sendSms(sms("098765 43210"), "sms_notifications");

        assertEquals("SMS", notificationDto.getChannel());
        assertEquals("LOGGED", notificationDto.getStatus());
        assertEquals("+919876543210", notificationDto.getTo());
        verifyNoInteractions(twilioSmsSender);
    }

    @Test
    void smsGoesThroughTwilioWhenConfigured() throws Exception {
        ReflectionTestUtils.setField(notificationService, "smsProvider", "twilio");
        when(twilioSmsSender.isConfigured()).thenReturn(true);
        when(twilioSmsSender.send("+14155550100", "Hello")).thenReturn(true);

        NotificationDto notificationDto = notificationService.sendSms(sms("+1 415 555 0100"), "api");

        assertEquals("SENT", notificationDto.getStatus());
    }

    @Test
    void twilioErrorIsRecordedAsFailed() throws Exception {
        ReflectionTestUtils.setField(notificationService, "smsProvider", "twilio");
        when(twilioSmsSender.isConfigured()).thenReturn(true);
        when(twilioSmsSender.send(anyString(), anyString())).thenThrow(new RuntimeException("401 Unauthorized"));

        NotificationDto notificationDto = notificationService.sendSms(sms("9876543210"), "api");

        assertEquals("FAILED", notificationDto.getStatus());
        assertEquals("401 Unauthorized", notificationDto.getError());
    }

    private EmailDto email(String to) {
        EmailDto emailDto = new EmailDto();
        emailDto.setTo(to);
        emailDto.setSubject("Welcome");
        emailDto.setBody("Hello");
        return emailDto;
    }

    private SmsDto sms(String to) {
        SmsDto smsDto = new SmsDto();
        smsDto.setTo(to);
        smsDto.setMessage("Hello");
        return smsDto;
    }
}
