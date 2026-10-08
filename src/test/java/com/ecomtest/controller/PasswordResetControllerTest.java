package com.ecomtest.controller;

import com.ecomtest.entity.User;
import com.ecomtest.repository.ResetTokenRepository;
import com.ecomtest.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PasswordResetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ResetTokenRepository resetTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private JavaMailSender mailSender;

    private User createUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("OldPassw0rd!"));
        return userRepository.save(user);
    }

    @Test
    void requestResetReturnsSameResponseForKnownAndUnknownEmail() throws Exception {
        createUser("known-user@example.com");

        String knownPayload = """
                {
                  "email": "known-user@example.com"
                }
                """;
        String unknownPayload = """
                {
                  "email": "unknown-user@example.com"
                }
                """;

        mockMvc.perform(post("/api/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(knownPayload))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unknownPayload))
                .andExpect(status().isOk());

        verify(mailSender, times(1)).send(any(org.springframework.mail.SimpleMailMessage.class));
    }

    @Test
    void rejectsExpiredToken() throws Exception {
        User user = createUser("expired-token-user@example.com");

        com.ecomtest.entity.ResetToken resetToken = new com.ecomtest.entity.ResetToken();
        resetToken.setToken(UUID.randomUUID().toString());
        resetToken.setUser(user);
        resetToken.setExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        resetToken.setUsed(false);
        resetTokenRepository.save(resetToken);

        String payload = """
                {
                  "token": "%s",
                  "newPassword": "NewPassw0rd!"
                }
                """.formatted(resetToken.getToken());

        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(user.getPasswordHash(), reloaded.getPasswordHash());
    }

    @Test
    void rejectsAlreadyUsedToken() throws Exception {
        User user = createUser("used-token-user@example.com");

        com.ecomtest.entity.ResetToken resetToken = new com.ecomtest.entity.ResetToken();
        resetToken.setToken(UUID.randomUUID().toString());
        resetToken.setUser(user);
        resetToken.setExpiresAt(Instant.now().plus(30, ChronoUnit.MINUTES));
        resetToken.setUsed(true);
        resetTokenRepository.save(resetToken);

        String payload = """
                {
                  "token": "%s",
                  "newPassword": "NewPassw0rd!"
                }
                """.formatted(resetToken.getToken());

        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsWeakPassword() throws Exception {
        User user = createUser("weak-password-user@example.com");

        com.ecomtest.entity.ResetToken resetToken = new com.ecomtest.entity.ResetToken();
        resetToken.setToken(UUID.randomUUID().toString());
        resetToken.setUser(user);
        resetToken.setExpiresAt(Instant.now().plus(30, ChronoUnit.MINUTES));
        resetToken.setUsed(false);
        resetTokenRepository.save(resetToken);

        String payload = """
                {
                  "token": "%s",
                  "newPassword": "weak"
                }
                """.formatted(resetToken.getToken());

        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());

        com.ecomtest.entity.ResetToken reloaded = resetTokenRepository.findByToken(resetToken.getToken()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertFalse(reloaded.isUsed());
    }

    @Test
    void resetsPasswordAndSendsNotificationOnSuccess() throws Exception {
        User user = createUser("success-user@example.com");
        String originalHash = user.getPasswordHash();

        com.ecomtest.entity.ResetToken resetToken = new com.ecomtest.entity.ResetToken();
        resetToken.setToken(UUID.randomUUID().toString());
        resetToken.setUser(user);
        resetToken.setExpiresAt(Instant.now().plus(30, ChronoUnit.MINUTES));
        resetToken.setUsed(false);
        resetTokenRepository.save(resetToken);

        mockMvc.perform(get("/api/auth/password-reset/confirm")
                        .param("token", resetToken.getToken()))
                .andExpect(status().isOk());

        String payload = """
                {
                  "token": "%s",
                  "newPassword": "NewPassw0rd!"
                }
                """.formatted(resetToken.getToken());

        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertNotEquals(originalHash, reloaded.getPasswordHash());

        com.ecomtest.entity.ResetToken reloadedToken = resetTokenRepository.findByToken(resetToken.getToken()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertTrue(reloadedToken.isUsed());

        verify(mailSender, times(1)).send(any(org.springframework.mail.SimpleMailMessage.class));
    }
}
