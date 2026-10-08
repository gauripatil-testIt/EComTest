package com.ecomtest.config;

import com.ecomtest.entity.Role;
import com.ecomtest.entity.User;
import com.ecomtest.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrapInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String bootstrapUsername;
    private final String bootstrapPassword;

    public AdminBootstrapInitializer(UserRepository userRepository,
                                      PasswordEncoder passwordEncoder,
                                      @Value("${app.admin.username:admin}") String bootstrapUsername,
                                      @Value("${app.admin.password:admin123}") String bootstrapPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.bootstrapUsername = bootstrapUsername;
        this.bootstrapPassword = bootstrapPassword;
    }

    @Override
    public void run(String... args) {
        if (userRepository.count() == 0) {
            User admin = new User();
            admin.setUsername(bootstrapUsername);
            admin.setPassword(passwordEncoder.encode(bootstrapPassword));
            admin.setRole(Role.ADMIN);
            userRepository.save(admin);
        }
    }
}
