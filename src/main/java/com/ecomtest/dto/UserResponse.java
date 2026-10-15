package com.ecomtest.dto;

import com.ecomtest.entity.Role;
import com.ecomtest.entity.User;

public class UserResponse {

    private Long id;
    private String username;
    private Role role;

    public static UserResponse from(User user) {
        UserResponse response = new UserResponse();
        response.id = user.getId();
        response.username = user.getUsername();
        response.role = user.getRole();
        return response;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public Role getRole() {
        return role;
    }
}
