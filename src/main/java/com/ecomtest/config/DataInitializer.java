package com.ecomtest.config;

import com.ecomtest.entity.User;
import com.ecomtest.entity.UserRole;
import com.ecomtest.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        seed("admin", "admin123", UserRole.ADMIN);
        seed("staff", "staff123", UserRole.STAFF);
        seed("customer", "customer123", UserRole.CUSTOMER);
        seed("customer2", "customer123", UserRole.CUSTOMER);
    }

    private void seed(String username, String rawPassword, UserRole role) {
        if (userRepository.existsByUsername(username)) {
            return;
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        userRepository.save(user);
    }
}
