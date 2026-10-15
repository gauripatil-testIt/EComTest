package com.ecomtest.controller;

import com.ecomtest.entity.PasswordResetToken;
import com.ecomtest.entity.User;
import com.ecomtest.repository.PasswordResetTokenRepository;
import com.ecomtest.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    private static final String FORGOT_MESSAGE = "If an account with that email exists, a password reset link has been sent.";
    private static final String RESET_MESSAGE = "Password has been reset successfully.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private JavaMailSender mailSender;

    @BeforeEach
    void clearMailInvocations() {
        clearInvocations(mailSender);
    }

    @Test
    void forgotPasswordReturnsSameResponseForKnownAndUnknownEmail() throws Exception {
        User user = createUser("known-" + System.currentTimeMillis() + "@example.com");

        String knownBody = mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(forgotPayload(user.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(FORGOT_MESSAGE))
                .andReturn().getResponse().getContentAsString();

        clearInvocations(mailSender);

        String unknownBody = mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(forgotPayload("unknown-" + System.currentTimeMillis() + "@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(FORGOT_MESSAGE))
                .andReturn().getResponse().getContentAsString();

        assertEquals(knownBody, unknownBody);
        verifyNoInteractions(mailSender);
    }

    @Test
    void forgotPasswordPersistsTokenAndSendsEmailForRegisteredUser() throws Exception {
        User user = createUser("persist-" + System.currentTimeMillis() + "@example.com");

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(forgotPayload(user.getEmail())))
                .andExpect(status().isOk());

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage message = captor.getValue();
        assertEquals(user.getEmail(), message.getTo()[0]);
        String text = message.getText();
        assertTrue(text.contains("/reset-password?token="), "reset link should carry the token");

        String token = text.substring(text.indexOf("token=") + "token=".length());
        PasswordResetToken saved = passwordResetTokenRepository.findByToken(token).orElseThrow();
        assertEquals(user.getId(), saved.getUser().getId());
        assertTrue(saved.getExpiryAt().isAfter(Instant.now()));
        assertTrue(saved.getExpiryAt().isBefore(Instant.now().plus(31, ChronoUnit.MINUTES)));
    }

    @Test
    void resetPasswordUpdatesHashAndMarksTokenUsed() throws Exception {
        User user = createUser("reset-" + System.currentTimeMillis() + "@example.com");
        String token = "valid-token-" + System.currentTimeMillis();
        createToken(user, token, Instant.now().plus(30, ChronoUnit.MINUTES), null);

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resetPayload(token, "Str0ngPass!word")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(RESET_MESSAGE));

        User updated = userRepository.findById(user.getId()).orElseThrow();
        assertNotEquals("old-hash", updated.getPasswordHash());
        assertTrue(passwordEncoder.matches("Str0ngPass!word", updated.getPasswordHash()));

        PasswordResetToken usedToken = passwordResetTokenRepository.findByToken(token).orElseThrow();
        assertNotNull(usedToken.getUsedAt());

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertEquals(user.getEmail(), captor.getValue().getTo()[0]);
        assertEquals("Password changed", captor.getValue().getSubject());
    }

    @Test
    void resetPasswordRejectsExpiredToken() throws Exception {
        User user = createUser("expired-" + System.currentTimeMillis() + "@example.com");
        String token = "expired-token-" + System.currentTimeMillis();
        createToken(user, token, Instant.now().minus(1, ChronoUnit.MINUTES), null);

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resetPayload(token, "Str0ngPass!word")))
                .andExpect(status().isBadRequest());

        assertEquals("old-hash", userRepository.findById(user.getId()).orElseThrow().getPasswordHash());
        assertNull(passwordResetTokenRepository.findByToken(token).orElseThrow().getUsedAt());
    }

    @Test
    void resetPasswordRejectsUsedToken() throws Exception {
        User user = createUser("used-" + System.currentTimeMillis() + "@example.com");
        String token = "used-token-" + System.currentTimeMillis();
        createToken(user, token, Instant.now().plus(30, ChronoUnit.MINUTES), Instant.now().minus(1, ChronoUnit.MINUTES));

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resetPayload(token, "Str0ngPass!word")))
                .andExpect(status().isBadRequest());

        assertEquals("old-hash", userRepository.findById(user.getId()).orElseThrow().getPasswordHash());
    }

    @Test
    void resetPasswordRejectsWeakPassword() throws Exception {
        User user = createUser("weak-" + System.currentTimeMillis() + "@example.com");
        String token = "weak-token-" + System.currentTimeMillis();
        createToken(user, token, Instant.now().plus(30, ChronoUnit.MINUTES), null);

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resetPayload(token, "weakpass")))
                .andExpect(status().isBadRequest());

        assertEquals("old-hash", userRepository.findById(user.getId()).orElseThrow().getPasswordHash());
        assertNull(passwordResetTokenRepository.findByToken(token).orElseThrow().getUsedAt());
    }

    private User createUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("old-hash");
        return userRepository.save(user);
    }

    private PasswordResetToken createToken(User user, String token, Instant expiryAt, Instant usedAt) {
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setToken(token);
        resetToken.setUser(user);
        resetToken.setExpiryAt(expiryAt);
        resetToken.setUsedAt(usedAt);
        return passwordResetTokenRepository.save(resetToken);
    }

    private String forgotPayload(String email) {
        return """
                {
                  "email": "%s"
                }
                """.formatted(email);
    }

    private String resetPayload(String token, String newPassword) {
        return """
                {
                  "token": "%s",
                  "newPassword": "%s"
                }
                """.formatted(token, newPassword);
    }
}
