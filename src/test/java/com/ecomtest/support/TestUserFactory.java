package com.ecomtest.support;

import tools.jackson.databind.ObjectMapper;
import com.ecomtest.entity.Role;
import com.ecomtest.entity.User;
import com.ecomtest.repository.UserRepository;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Seeds a test user with a given role and returns a JWT obtained by logging in
 * via the real /api/auth/login endpoint, so controller tests can exercise
 * authenticated requests without duplicating the login dance per test class.
 */
public final class TestUserFactory {

    private static final AtomicInteger USER_COUNTER = new AtomicInteger();
    private static final String TEST_PASSWORD = "secret123";

    private TestUserFactory() {
    }

    public static String createUserAndToken(MockMvc mockMvc,
                                             ObjectMapper objectMapper,
                                             UserRepository userRepository,
                                             PasswordEncoder passwordEncoder,
                                             Role role) throws Exception {
        String username = "test-user-" + role.name() + "-" + USER_COUNTER.incrementAndGet();

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(TEST_PASSWORD));
        user.setRole(role);
        user.setEnabled(true);
        userRepository.save(user);

        String loginPayload = """
                {
                  "username": "%s",
                  "password": "%s"
                }
                """.formatted(username, TEST_PASSWORD);

        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).get("token").asText();
    }
}
