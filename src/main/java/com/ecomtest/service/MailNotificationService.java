package com.ecomtest.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Service
public class MailNotificationService {

    private static final String RESET_LINK_TEMPLATE = "templates/mail/password-reset-link.txt";
    private static final String PASSWORD_CHANGED_TEMPLATE = "templates/mail/password-changed.txt";

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String resetBaseUrl;

    public MailNotificationService(JavaMailSender mailSender,
                                    @Value("${app.mail.from}") String fromAddress,
                                    @Value("${app.reset.base-url}") String resetBaseUrl) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.resetBaseUrl = resetBaseUrl;
    }

    public void sendPasswordResetLink(String toEmail, String rawToken) {
        String link = resetBaseUrl + "?token=" + rawToken;
        String body = loadTemplate(RESET_LINK_TEMPLATE).replace("{{link}}", link);
        send(toEmail, "Reset your password", body);
    }

    public void sendPasswordChanged(String toEmail) {
        String body = loadTemplate(PASSWORD_CHANGED_TEMPLATE);
        send(toEmail, "Your password has been changed", body);
    }

    private void send(String toEmail, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }

    private String loadTemplate(String classpathLocation) {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(classpathLocation)) {
            if (inputStream == null) {
                throw new IllegalStateException("Mail template not found: " + classpathLocation);
            }
            return StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load mail template: " + classpathLocation, e);
        }
    }
}
