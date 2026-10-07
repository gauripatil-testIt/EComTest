package com.ecomtest.service;

import com.ecomtest.entity.PasswordResetToken;
import com.ecomtest.entity.User;
import com.ecomtest.exception.InvalidOrExpiredTokenException;
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

@Service
public class PasswordResetService {

    private static final int TOKEN_BYTE_LENGTH = 32;
    private static final long TOKEN_VALIDITY_MINUTES = 30;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final String resetLinkBaseUrl;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordResetService(UserRepository userRepository,
                                 PasswordResetTokenRepository passwordResetTokenRepository,
                                 PasswordEncoder passwordEncoder,
                                 EmailService emailService,
                                 @Value("${app.password-reset.link-base-url}") String resetLinkBaseUrl) {
        this.userRepository = userRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.resetLinkBaseUrl = resetLinkBaseUrl;
    }

    @Transactional
    public void requestReset(String email) {
        Optional<User> userOptional = userRepository.findByEmail(email);
        if (userOptional.isEmpty()) {
            return;
        }
        User user = userOptional.get();

        String token = generateToken();
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setToken(token);
        resetToken.setUser(user);
        resetToken.setExpiresAt(Instant.now().plus(TOKEN_VALIDITY_MINUTES, ChronoUnit.MINUTES));
        passwordResetTokenRepository.save(resetToken);

        String resetLink = resetLinkBaseUrl + "?token=" + token;
        emailService.sendPasswordResetEmail(user.getEmail(), resetLink);
    }

    @Transactional
    public void confirmReset(String token, String newPassword) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(token)
                .orElseThrow(() -> new InvalidOrExpiredTokenException("Invalid or expired password reset token"));

        if (resetToken.getUsedAt() != null) {
            throw new InvalidOrExpiredTokenException("Invalid or expired password reset token");
        }
        if (resetToken.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidOrExpiredTokenException("Invalid or expired password reset token");
        }

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        resetToken.setUsedAt(Instant.now());
        passwordResetTokenRepository.save(resetToken);

        emailService.sendPasswordChangedEmail(user.getEmail());
    }

    private String generateToken() {
        byte[] randomBytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
