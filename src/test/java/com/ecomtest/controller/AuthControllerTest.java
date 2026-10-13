package com.ecomtest.controller;

import tools.jackson.databind.ObjectMapper;
import com.ecomtest.entity.Role;
import com.ecomtest.service.UserService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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
    private UserService userService;

    private String loginPayload(String username, String password) {
        return """
                {
                  "username": "%s",
                  "password": "%s"
                }
                """.formatted(username, password);
    }

    @Test
    void loginWithCorrectCredentialsReturnsToken() throws Exception {
        userService.create("auth-login-user", "Password123!", Role.CUSTOMER);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload("auth-login-user", "Password123!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.username").value("auth-login-user"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    void loginWithWrongCredentialsIsRejected() throws Exception {
        userService.create("auth-login-wrong-user", "Password123!", Role.CUSTOMER);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload("auth-login-wrong-user", "WrongPassword!")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registerCreatesCustomerWithoutPassword() throws Exception {
        String registerPayload = """
                {
                  "username": "auth-register-user",
                  "password": "Password123!"
                }
                """;

        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("auth-register-user"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andReturn().getResponse().getContentAsString();

        Assertions.assertFalse(response.contains("password"),
                "Register response should not contain a password field");
    }
}
