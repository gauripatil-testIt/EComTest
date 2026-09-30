package com.ecomtest.controller;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String login(String username, String password) throws Exception {
        String payload = """
                {
                  "username": "%s",
                  "password": "%s"
                }
                """.formatted(username, password);

        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }

    private String adminAuthHeader() throws Exception {
        return "Bearer " + login("admin", "admin123");
    }

    private String staffAuthHeader() throws Exception {
        return "Bearer " + login("staff", "staff123");
    }

    private String customerAuthHeader() throws Exception {
        return "Bearer " + login("customer", "customer123");
    }

    private String userPayload(String username) {
        return """
                {
                  "username": "%s",
                  "password": "secret123",
                  "roles": ["STAFF"]
                }
                """.formatted(username);
    }

    @Test
    void adminCanCreateAndListUsers() throws Exception {
        String admin = adminAuthHeader();

        String response = mockMvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userPayload("new-staff-user")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("new-staff-user"))
                .andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertFalse(response.contains("passwordHash"));
        org.junit.jupiter.api.Assertions.assertFalse(response.contains("secret123"));

        String listResponse = mockMvc.perform(get("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertFalse(listResponse.contains("passwordHash"));
    }

    @Test
    void staffAndCustomerCannotManageUsers() throws Exception {
        String staff = staffAuthHeader();
        String customer = customerAuthHeader();

        mockMvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, staff)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userPayload("blocked-user-1")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, staff))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userPayload("blocked-user-2")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, customer))
                .andExpect(status().isForbidden());
    }
}
