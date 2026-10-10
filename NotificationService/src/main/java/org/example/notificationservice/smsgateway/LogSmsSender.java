package org.example.notificationservice.smsgateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LogSmsSender implements ISmsSender {

    @Override
    public boolean send(String to, String message) {
        log.info("SMS provider not configured, logging SMS instead.\nTo: {}\n{}", to, message);
        return false;
    }

    @Override
    public boolean isConfigured() {
        return true;
    }
}
