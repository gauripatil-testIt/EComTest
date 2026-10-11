package com.ecomtest.dto;

import com.ecomtest.entity.Role;
import com.ecomtest.entity.User;

public class UserResponse {

    private Long id;
    private String email;
    private Role role;

    public static UserResponse from(User user) {
        UserResponse response = new UserResponse();
        response.id = user.getId();
        response.email = user.getEmail();
        response.role = user.getRole();
        return response;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public Role getRole() {
        return role;
    }
}
