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
        message.setText("We received a request to reset your password. "
                + "Use the link below to set a new password. This link expires in 30 minutes.\n\n"
                + resetLink
                + "\n\nIf you did not request this, you can safely ignore this email.");
        mailSender.send(message);
    }

    public void sendPasswordChangedEmail(String to) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("Your password has been changed");
        message.setText("This is a confirmation that the password for your account has just been changed. "
                + "If you did not make this change, please contact support immediately.");
        mailSender.send(message);
    }
}
