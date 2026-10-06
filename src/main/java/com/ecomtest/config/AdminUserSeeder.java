package com.ecomtest.config;

import com.ecomtest.entity.Role;
import com.ecomtest.entity.User;
import com.ecomtest.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminUserSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String bootstrapAdminUsername;
    private final String bootstrapAdminPassword;

    public AdminUserSeeder(UserRepository userRepository,
                            PasswordEncoder passwordEncoder,
                            @Value("${security.bootstrap-admin.username}") String bootstrapAdminUsername,
                            @Value("${security.bootstrap-admin.password}") String bootstrapAdminPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.bootstrapAdminUsername = bootstrapAdminUsername;
        this.bootstrapAdminPassword = bootstrapAdminPassword;
    }

    @Override
    public void run(String... args) {
        if (userRepository.findByUsername(bootstrapAdminUsername).isPresent()) {
            return;
        }

        User admin = new User();
        admin.setUsername(bootstrapAdminUsername);
        admin.setPassword(passwordEncoder.encode(bootstrapAdminPassword));
        admin.setRole(Role.ADMIN);
        userRepository.save(admin);
    }
}
