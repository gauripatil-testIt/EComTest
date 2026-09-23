package com.ecomtest.config;

import com.ecomtest.entity.Role;
import com.ecomtest.entity.User;
import com.ecomtest.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) {
            return;
        }

        User admin = new User();
        admin.setUsername("admin");
        admin.setPasswordHash(passwordEncoder.encode("admin123"));
        admin.setRole(Role.ADMIN);
        userRepository.save(admin);

        User staff = new User();
        staff.setUsername("staff");
        staff.setPasswordHash(passwordEncoder.encode("staff123"));
        staff.setRole(Role.STAFF);
        userRepository.save(staff);

        User customer = new User();
        customer.setUsername("customer");
        customer.setPasswordHash(passwordEncoder.encode("customer123"));
        customer.setRole(Role.CUSTOMER);
        userRepository.save(customer);
    }
}
