package com.ecomtest.service;

import com.ecomtest.dto.ResetPasswordRequest;
import com.ecomtest.entity.PasswordResetToken;
import com.ecomtest.entity.User;
import com.ecomtest.exception.InvalidResetTokenException;
import com.ecomtest.repository.PasswordResetTokenRepository;
import com.ecomtest.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class AuthService {

    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$");

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final long resetTokenTtlMinutes;
    private final String baseUrl;

    public AuthService(
            UserRepository userRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            EmailService emailService,
            PasswordEncoder passwordEncoder,
            @Value("${app.reset-token-ttl-minutes:30}") long resetTokenTtlMinutes,
            @Value("${app.base-url:http://localhost:8080}") String baseUrl) {
        this.userRepository = userRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.resetTokenTtlMinutes = resetTokenTtlMinutes;
        this.baseUrl = baseUrl;
    }

    @Transactional
    public void forgotPassword(String email) {
        Optional<User> user = userRepository.findByEmail(email);
        if (user.isEmpty()) {
            return;
        }

        String token = generateToken();
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setToken(token);
        resetToken.setUser(user.get());
        resetToken.setExpiryAt(Instant.now().plus(resetTokenTtlMinutes, ChronoUnit.MINUTES));
        passwordResetTokenRepository.save(resetToken);

        emailService.sendPasswordResetEmail(user.get().getEmail(), buildResetLink(token));
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(request.getToken())
                .orElseThrow(() -> new InvalidResetTokenException("Invalid or expired password reset token."));

        if (resetToken.getUsedAt() != null) {
            throw new InvalidResetTokenException("This password reset token has already been used.");
        }
        if (resetToken.getExpiryAt().isBefore(Instant.now())) {
            throw new InvalidResetTokenException("This password reset token has expired.");
        }
        if (!PASSWORD_PATTERN.matcher(request.getNewPassword()).matches()) {
            throw new InvalidResetTokenException("New password does not meet complexity requirements.");
        }

        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        resetToken.setUsedAt(Instant.now());
        passwordResetTokenRepository.save(resetToken);

        emailService.sendPasswordChangedEmail(user.getEmail());
    }

    public String generateToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String buildResetLink(String token) {
        return baseUrl + "/reset-password?token=" + token;
    }
}
