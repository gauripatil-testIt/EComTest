package com.ecomtest.controller;

import com.ecomtest.dto.PasswordResetConfirmRequest;
import com.ecomtest.dto.PasswordResetRequest;
import com.ecomtest.service.PasswordResetService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final PasswordResetService passwordResetService;

    public AuthController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/password-reset/request")
    public ResponseEntity<Map<String, Object>> requestReset(@Valid @RequestBody PasswordResetRequest request) {
        passwordResetService.requestReset(request.getEmail());
        return ResponseEntity.ok(Map.of(
                "message", "If an account with that email exists, a password reset link has been sent."));
    }

    @PostMapping("/password-reset/confirm")
    public ResponseEntity<Map<String, Object>> confirmReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordResetService.confirmReset(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok(Map.of("message", "Your password has been changed successfully."));
    }
}
