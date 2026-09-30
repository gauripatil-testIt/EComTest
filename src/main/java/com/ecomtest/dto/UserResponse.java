package com.ecomtest.dto;

import com.ecomtest.entity.Role;
import com.ecomtest.entity.User;

import java.util.Set;

public class UserResponse {

    private Long id;
    private String username;
    private Set<Role> roles;

    public static UserResponse from(User user) {
        UserResponse response = new UserResponse();
        response.id = user.getId();
        response.username = user.getUsername();
        response.roles = user.getRoles();
        return response;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public Set<Role> getRoles() {
        return roles;
    }
}
