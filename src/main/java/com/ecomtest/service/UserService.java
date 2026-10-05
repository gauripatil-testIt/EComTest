package com.ecomtest.service;

import com.ecomtest.dto.CreateUserRequest;
import com.ecomtest.entity.Role;
import com.ecomtest.entity.User;
import com.ecomtest.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User create(CreateUserRequest request) {
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(request.getRole());
        return userRepository.save(user);
    }

    public List<User> list() {
        return userRepository.findAll();
    }

    public User createIfAbsent(String username, String rawPassword, Role role) {
        return userRepository.findByUsername(username)
                .orElseGet(() -> {
                    User user = new User();
                    user.setUsername(username);
                    user.setPassword(passwordEncoder.encode(rawPassword));
                    user.setRole(role);
                    return userRepository.save(user);
                });
    }
}
