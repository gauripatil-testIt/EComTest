package com.ecomtest.controller;

import com.ecomtest.dto.PasswordResetConfirmRequest;
import com.ecomtest.dto.PasswordResetRequest;
import com.ecomtest.service.PasswordResetService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/password-reset")
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/request")
    public ResponseEntity<Void> requestReset(@Valid @RequestBody PasswordResetRequest request) {
        passwordResetService.requestReset(request.getEmail());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/confirm")
    public ResponseEntity<Void> confirmReset(@RequestParam String token,
                                              @Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordResetService.confirmReset(token, request.getNewPassword());
        return ResponseEntity.ok().build();
    }
}
