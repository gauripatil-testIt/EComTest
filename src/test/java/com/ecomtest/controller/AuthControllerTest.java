package com.ecomtest.controller;

import tools.jackson.databind.ObjectMapper;
import com.ecomtest.entity.Role;
import com.ecomtest.entity.User;
import com.ecomtest.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

    private static final String RAW_PASSWORD = "Passw0rd!";

    @BeforeEach
    void setUp() {
        userRepository.findByUsername("auth-test-user").orElseGet(() -> {
            User user = new User();
            user.setUsername("auth-test-user");
            user.setPassword(passwordEncoder.encode(RAW_PASSWORD));
            user.setRole(Role.ADMIN);
            return userRepository.save(user);
        });
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
    void loginSucceedsWithValidCredentialsAndTokenSubjectIsUserId() throws Exception {
        User user = userRepository.findByUsername("auth-test-user").orElseThrow();

        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload("auth-test-user", RAW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(response).get("token").asText();
        String[] parts = token.split("\\.");
        String payloadJson = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
        String subject = objectMapper.readTree(payloadJson).get("sub").asText();

        org.junit.jupiter.api.Assertions.assertEquals(String.valueOf(user.getId()), subject);
    }

    @Test
    void loginFailsWithInvalidPassword() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload("auth-test-user", "wrong-password")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginFailsWithUnknownUsername() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload("no-such-user", RAW_PASSWORD)))
                .andExpect(status().isUnauthorized());
    }
}
