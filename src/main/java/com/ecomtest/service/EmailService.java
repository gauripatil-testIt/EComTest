package com.ecomtest.service;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendPasswordResetEmail(String to, String resetLink) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("Password Reset Request");
        message.setText("We received a request to reset your password. Click the link below to reset it:\n\n"
                + resetLink
                + "\n\nThis link will expire in 30 minutes. If you did not request a password reset, please ignore "
                + "this email.");
        mailSender.send(message);
    }

    public void sendPasswordChangedEmail(String to) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("Your Password Has Been Changed");
        message.setText("This is a confirmation that the password for your account has just been changed. "
                + "If you did not make this change, please contact support immediately.");
        mailSender.send(message);
    }
}
