package com.ecomtest.support;

import com.ecomtest.entity.Role;
import com.ecomtest.entity.User;
import com.ecomtest.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Test helper that seeds ADMIN/STAFF/CUSTOMER users (with a BCrypt-hashed
 * password) so controller tests can authenticate against the real
 * /api/auth/login endpoint instead of mocking security.
 */
public class TestUserSeeder {

    public static final String DEFAULT_PASSWORD = "password";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public TestUserSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User seedAdmin(String username) {
        return seed(username, Role.ADMIN);
    }

    public User seedStaff(String username) {
        return seed(username, Role.STAFF);
    }

    public User seedCustomer(String username) {
        return seed(username, Role.CUSTOMER);
    }

    private User seed(String username, Role role) {
        return userRepository.findByUsername(username).orElseGet(() -> {
            User user = new User();
            user.setUsername(username);
            user.setEmail(username + "@example.com");
            user.setPasswordHash(passwordEncoder.encode(DEFAULT_PASSWORD));
            user.setRole(role);
            return userRepository.save(user);
        });
    }
}
