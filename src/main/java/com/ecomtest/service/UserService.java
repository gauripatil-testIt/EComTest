package com.ecomtest.service;

import com.ecomtest.dto.UserRequest;
import com.ecomtest.entity.User;
import com.ecomtest.exception.ResourceNotFoundException;
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

    public User create(UserRequest request) {
        User user = new User();
        applyRequest(user, request);
        return userRepository.save(user);
    }

    public User get(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    public List<User> list() {
        return userRepository.findAll();
    }

    public User update(Long id, UserRequest request) {
        User user = get(id);
        applyRequest(user, request);
        return userRepository.save(user);
    }

    public void delete(Long id) {
        User user = get(id);
        userRepository.delete(user);
    }

    private void applyRequest(User user, UserRequest request) {
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(request.getRole());
    }
}
