package com.ecomtest.service;

import com.ecomtest.entity.PasswordResetToken;
import com.ecomtest.entity.User;
import com.ecomtest.exception.InvalidResetTokenException;
import com.ecomtest.repository.PasswordResetTokenRepository;
import com.ecomtest.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

@Service
public class PasswordResetService {

    private static final Duration TOKEN_TTL = Duration.ofMinutes(30);

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final MailNotificationService mailNotificationService;
    private final Clock clock;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordResetService(UserRepository userRepository,
                                 PasswordResetTokenRepository passwordResetTokenRepository,
                                 MailNotificationService mailNotificationService,
                                 Clock clock) {
        this.userRepository = userRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.mailNotificationService = mailNotificationService;
        this.clock = clock;
    }

    public void requestReset(String email) {
        Optional<User> userOptional = userRepository.findByEmailIgnoreCase(email);
        if (userOptional.isEmpty()) {
            return;
        }
        User user = userOptional.get();

        String rawToken = generateRawToken();
        String tokenHash = hashToken(rawToken);

        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash(tokenHash);
        token.setExpiresAt(Instant.now(clock).plus(TOKEN_TTL));
        passwordResetTokenRepository.save(token);

        mailNotificationService.sendPasswordResetLink(user.getEmail(), rawToken);
    }

    public void confirmReset(String rawToken, String newPassword) {
        String tokenHash = hashToken(rawToken);
        PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new InvalidResetTokenException("Invalid or expired reset token"));

        if (token.getExpiresAt().isBefore(Instant.now(clock))) {
            throw new InvalidResetTokenException("Invalid or expired reset token");
        }

        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        passwordResetTokenRepository.deleteByUser(user);

        mailNotificationService.sendPasswordChanged(user.getEmail());
    }

    private String generateRawToken() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}

