package com.ecomtest.controller;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.ecomtest.entity.Role;
import com.ecomtest.entity.User;
import com.ecomtest.repository.UserRepository;

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

    private void ensureUser(String username, String rawPassword, Role role) {
        if (userRepository.findByUsername(username).isEmpty()) {
            User user = new User();
            user.setUsername(username);
            user.setPassword(passwordEncoder.encode(rawPassword));
            user.setRole(role);
            user.setEnabled(true);
            userRepository.save(user);
        }
    }

    @Test
    void loginWithValidCredentialsReturnsToken() throws Exception {
        ensureUser("auth-valid-user", "correct-password", Role.ADMIN);

        String payload = """
                {
                  "username": "auth-valid-user",
                  "password": "correct-password"
                }
                """;

        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(response).get("token").asText();
        org.hamcrest.MatcherAssert.assertThat(token, org.hamcrest.Matchers.not(org.hamcrest.Matchers.emptyOrNullString()));
        org.hamcrest.MatcherAssert.assertThat(response, org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("password")));
    }

    @Test
    void loginWithInvalidCredentialsReturnsUnauthorized() throws Exception {
        ensureUser("auth-invalid-user", "correct-password", Role.CUSTOMER);

        String payload = """
                {
                  "username": "auth-invalid-user",
                  "password": "wrong-password"
                }
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginWithUnknownUserReturnsUnauthorized() throws Exception {
        String payload = """
                {
                  "username": "no-such-user",
                  "password": "whatever"
                }
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized());
    }
}
