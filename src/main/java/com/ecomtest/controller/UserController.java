package com.ecomtest.controller;

import com.ecomtest.dto.UserRequest;
import com.ecomtest.dto.UserResponse;
import com.ecomtest.entity.User;
import com.ecomtest.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public UserResponse create(@Valid @RequestBody UserRequest request) {
        User user = userService.create(request);
        return UserResponse.from(user);
    }

    @RequestMapping("/list")
    public List<UserResponse> list() {
        return userService.list().stream().map(UserResponse::from).toList();
    }
}
