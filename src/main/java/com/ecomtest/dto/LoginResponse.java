package com.ecomtest.dto;

import com.ecomtest.entity.Role;

public class LoginResponse {

    private String token;
    private Role role;

    public LoginResponse() {
    }

    public LoginResponse(String token, Role role) {
        this.token = token;
        this.role = role;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }
}
