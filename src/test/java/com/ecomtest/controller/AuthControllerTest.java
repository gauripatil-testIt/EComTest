package com.ecomtest.controller;

import tools.jackson.databind.ObjectMapper;
import com.ecomtest.entity.Role;
import com.ecomtest.entity.User;
import com.ecomtest.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private void createUser(String username, String rawPassword, Role role) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        user.setEnabled(true);
        userRepository.save(user);
    }

    private String loginPayload(String username, String password) {
        return """
                {
                  "username": "%s",
                  "password": "%s"
                }
                """.formatted(username, password);
    }

    @Test
    void loginWithValidCredentialsReturnsToken() throws Exception {
        createUser("auth-valid-user", "secret123", Role.ADMIN);

        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload("auth-valid-user", "secret123")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(response).get("token").asText();
        org.junit.jupiter.api.Assertions.assertFalse(token.isBlank());
    }

    @Test
    void loginWithInvalidCredentialsReturnsUnauthorized() throws Exception {
        createUser("auth-invalid-user", "secret123", Role.ADMIN);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload("auth-invalid-user", "wrong-password")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticatedRequestToProtectedEndpointReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tamperedTokenReturnsUnauthorized() throws Exception {
        createUser("auth-tampered-user", "secret123", Role.ADMIN);

        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload("auth-tampered-user", "secret123")))
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(response).get("token").asText();
        String tamperedToken = token.substring(0, token.length() - 1) + (token.endsWith("a") ? "b" : "a");

        mockMvc.perform(get("/api/products")
                        .header("Authorization", "Bearer " + tamperedToken))
                .andExpect(status().isUnauthorized());
    }
}
