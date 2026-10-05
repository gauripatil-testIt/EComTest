package com.ecomtest.config;

import com.ecomtest.entity.Role;
import com.ecomtest.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrapRunner implements CommandLineRunner {

    private final UserService userService;
    private final String adminUsername;
    private final String adminPassword;

    public AdminBootstrapRunner(UserService userService,
                                 @Value("${app.admin.bootstrap-username}") String adminUsername,
                                 @Value("${app.admin.bootstrap-password}") String adminPassword) {
        this.userService = userService;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(String... args) {
        userService.createIfAbsent(adminUsername, adminPassword, Role.ADMIN);
    }
}
