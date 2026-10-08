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

    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("Password Reset Request");
        message.setText("We received a request to reset your password. "
                + "Use the link below to reset it. This link will expire in 30 minutes.\n\n"
                + resetLink
                + "\n\nIf you did not request a password reset, you can safely ignore this email.");
        mailSender.send(message);
    }

    public void sendPasswordChangedEmail(String toEmail) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("Your Password Has Been Changed");
        message.setText("This is a confirmation that the password for your account has been successfully changed. "
                + "If you did not make this change, please contact support immediately.");
        mailSender.send(message);
    }
}
