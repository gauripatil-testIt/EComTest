package com.ecomtest.dto;

import com.ecomtest.entity.Role;

public class LoginResponse {

    private String token;
    private String username;
    private Role role;

    public LoginResponse(String token, String username, Role role) {
        this.token = token;
        this.username = username;
        this.role = role;
    }

    public String getToken() {
        return token;
    }

    public String getUsername() {
        return username;
    }

    public Role getRole() {
        return role;
    }
}
