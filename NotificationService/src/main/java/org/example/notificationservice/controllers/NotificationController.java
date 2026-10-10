package org.example.notificationservice.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.notificationservice.clients.AuthClient;
import org.example.notificationservice.dtos.EmailDto;
import org.example.notificationservice.dtos.NotificationDto;
import org.example.notificationservice.dtos.SmsDto;
import org.example.notificationservice.services.INotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Notifications", description = "Admin tools to send a test email or SMS and see recent notifications")
@RestController
@RequestMapping("/notifications")
public class NotificationController {

    @Autowired
    private INotificationService notificationService;

    @Autowired
    private AuthClient authClient;

    @Operation(summary = "Send a test email (admin only)")
    @PostMapping("/email")
    public NotificationDto sendEmail(@Valid @RequestBody EmailDto emailDto,
                                     @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        authClient.requireAdmin(authorization);
        return notificationService.sendEmail(emailDto, "api");
    }

    @Operation(summary = "Send a test SMS (admin only)")
    @PostMapping("/sms")
    public NotificationDto sendSms(@Valid @RequestBody SmsDto smsDto,
                                   @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        authClient.requireAdmin(authorization);
        return notificationService.sendSms(smsDto, "api");
    }

    @Operation(summary = "Recent notifications, newest first (admin only)")
    @GetMapping
    public List<NotificationDto> getRecentNotifications(@Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        authClient.requireAdmin(authorization);
        return notificationService.getRecentNotifications();
    }
}
