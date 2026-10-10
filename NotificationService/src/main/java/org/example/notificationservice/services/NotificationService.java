package org.example.notificationservice.services;

import lombok.extern.slf4j.Slf4j;
import org.example.notificationservice.dtos.EmailDto;
import org.example.notificationservice.dtos.NotificationDto;
import org.example.notificationservice.dtos.SmsDto;
import org.example.notificationservice.smsgateway.ISmsSender;
import org.example.notificationservice.smsgateway.LogSmsSender;
import org.example.notificationservice.smsgateway.TwilioSmsSender;
import org.example.notificationservice.utils.EmailUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.mail.Authenticator;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Properties;

@Slf4j
@Service
public class NotificationService implements INotificationService {

    @Value("${app.mail.host}")
    private String smtpHost;

    @Value("${app.mail.port}")
    private String smtpPort;

    @Value("${app.mail.username}")
    private String smtpUsername;

    @Value("${app.mail.password}")
    private String smtpPassword;

    @Value("${app.mail.from}")
    private String defaultFrom;

    @Value("${app.mail.from-name}")
    private String fromName;

    @Value("${app.notifications.history-size:100}")
    private int historySize;

    @Value("${app.sms.provider:log}")
    private String smsProvider;

    @Value("${app.sms.default-country-code:+91}")
    private String defaultCountryCode;

    @Autowired
    private LogSmsSender logSmsSender;

    @Autowired
    private TwilioSmsSender twilioSmsSender;

    private final Deque<NotificationDto> history = new ArrayDeque<>();

    private Session session;

    @Override
    public NotificationDto sendEmail(EmailDto emailDto, String source) {
        NotificationDto notificationDto = new NotificationDto();
        notificationDto.setChannel("EMAIL");
        notificationDto.setSource(source);
        notificationDto.setTo(emailDto.getTo());
        notificationDto.setSubject(emailDto.getSubject());
        notificationDto.setBody(emailDto.getBody());

        if (!isSmtpConfigured()) {
            log.info("SMTP not configured, logging email instead.\nTo: {}\nSubject: {}\n{}",
                    emailDto.getTo(), emailDto.getSubject(), emailDto.getBody());
            notificationDto.setStatus("LOGGED");
        } else {
            try {
                String from = smtpUsername;
                if (defaultFrom != null && !defaultFrom.isBlank()) {
                    from = defaultFrom;
                }
                EmailUtil.sendEmail(getSession(), from, fromName, emailDto.getTo(), emailDto.getSubject(), emailDto.getBody());
                log.info("Email '{}' sent to {}", emailDto.getSubject(), emailDto.getTo());
                notificationDto.setStatus("SENT");
            } catch (Exception exception) {
                log.error("Could not send email '{}' to {}: {}", emailDto.getSubject(), emailDto.getTo(), exception.getMessage());
                notificationDto.setStatus("FAILED");
                notificationDto.setError(exception.getMessage());
            }
        }
        record(notificationDto);
        return notificationDto;
    }

    @Override
    public NotificationDto sendSms(SmsDto smsDto, String source) {
        String to = toInternationalNumber(smsDto.getTo());
        NotificationDto notificationDto = new NotificationDto();
        notificationDto.setChannel("SMS");
        notificationDto.setSource(source);
        notificationDto.setTo(to);
        notificationDto.setBody(smsDto.getMessage());

        ISmsSender sender;
        if ("twilio".equalsIgnoreCase(smsProvider) && twilioSmsSender.isConfigured()) {
            sender = twilioSmsSender;
        } else {
            sender = logSmsSender;
        }
        try {
            boolean delivered = sender.send(to, smsDto.getMessage());
            if (delivered) {
                notificationDto.setStatus("SENT");
                log.info("SMS sent to {}", to);
            } else {
                notificationDto.setStatus("LOGGED");
            }
        } catch (Exception exception) {
            log.error("Could not send SMS to {}: {}", to, exception.getMessage());
            notificationDto.setStatus("FAILED");
            notificationDto.setError(exception.getMessage());
        }
        record(notificationDto);
        return notificationDto;
    }

    @Override
    public List<NotificationDto> getRecentNotifications() {
        synchronized (history) {
            return new ArrayList<>(history);
        }
    }

    // SMS providers need E.164 numbers; local numbers get the default country code.
    private String toInternationalNumber(String phoneNumber) {
        String digits = phoneNumber.replaceAll("[^0-9+]", "");
        if (digits.startsWith("+")) {
            return digits;
        }
        if (digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        return defaultCountryCode + digits;
    }

    private boolean isSmtpConfigured() {
        return smtpUsername != null && !smtpUsername.isBlank() && smtpPassword != null && !smtpPassword.isBlank();
    }

    private synchronized Session getSession() {
        if (session == null) {
            Properties props = new Properties();
            props.put("mail.smtp.host", smtpHost); //SMTP Host
            props.put("mail.smtp.port", smtpPort); //TLS Port
            props.put("mail.smtp.auth", "true"); //enable authentication
            props.put("mail.smtp.starttls.enable", "true"); //enable STARTTLS
            props.put("mail.smtp.connectiontimeout", "10000");
            props.put("mail.smtp.timeout", "10000");

            //create Authenticator object to pass in Session.getInstance argument
            Authenticator auth = new Authenticator() {
                //override the getPasswordAuthentication method
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(smtpUsername, smtpPassword);
                }
            };
            session = Session.getInstance(props, auth);
        }
        return session;
    }

    private void record(NotificationDto notificationDto) {
        synchronized (history) {
            history.addFirst(notificationDto);
            while (history.size() > historySize) {
                history.removeLast();
            }
        }
    }
}
