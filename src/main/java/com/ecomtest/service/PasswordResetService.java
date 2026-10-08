package com.ecomtest.service;

import com.ecomtest.entity.ResetToken;
import com.ecomtest.entity.User;
import com.ecomtest.exception.InvalidTokenException;
import com.ecomtest.repository.ResetTokenRepository;
import com.ecomtest.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Service
public class PasswordResetService {

    private final UserRepository userRepository;
    private final ResetTokenRepository resetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final long tokenExpiryMinutes;
    private final String baseUrl;

    public PasswordResetService(UserRepository userRepository,
                                 ResetTokenRepository resetTokenRepository,
                                 PasswordEncoder passwordEncoder,
                                 EmailService emailService,
                                 @Value("${app.password-reset.token-expiry-minutes}") long tokenExpiryMinutes,
                                 @Value("${app.password-reset.base-url}") String baseUrl) {
        this.userRepository = userRepository;
        this.resetTokenRepository = resetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.tokenExpiryMinutes = tokenExpiryMinutes;
        this.baseUrl = baseUrl;
    }

    public void requestPasswordReset(String email) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return;
        }
        User user = userOpt.get();

        ResetToken resetToken = new ResetToken();
        resetToken.setToken(UUID.randomUUID().toString());
        resetToken.setUser(user);
        resetToken.setExpiresAt(Instant.now().plus(tokenExpiryMinutes, ChronoUnit.MINUTES));
        resetToken.setUsed(false);
        resetTokenRepository.save(resetToken);

        String resetLink = baseUrl + "?token=" + resetToken.getToken();
        emailService.sendPasswordResetEmail(user.getEmail(), resetLink);
    }

    public void confirmTokenValid(String token) {
        getValidToken(token);
    }

    public void resetPassword(String token, String newPassword) {
        ResetToken resetToken = getValidToken(token);

        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        resetToken.setUsed(true);
        resetTokenRepository.save(resetToken);

        emailService.sendPasswordChangedEmail(user.getEmail());
    }

    private ResetToken getValidToken(String token) {
        ResetToken resetToken = resetTokenRepository.findByToken(token)
                .orElseThrow(() -> new InvalidTokenException("Invalid password reset token"));

        if (resetToken.isUsed()) {
            throw new InvalidTokenException("Password reset token has already been used");
        }

        if (resetToken.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidTokenException("Password reset token has expired");
        }

        return resetToken;
    }
}
