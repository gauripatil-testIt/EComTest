package com.ecomtest.dto;

import com.ecomtest.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;

public class PasswordResetConfirmRequest {

    @NotBlank
    private String token;

    @NotBlank
    @ValidPassword
    private String newPassword;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
