package org.example.notificationservice.smsgateway;

public interface ISmsSender {
    // Returns true when the message was delivered to a provider, false when it was only logged.
    boolean send(String to, String message) throws Exception;

    boolean isConfigured();
}
