package com.ecomtest.service;

import com.ecomtest.dto.AuthResponse;
import com.ecomtest.dto.LoginRequest;
import com.ecomtest.dto.RegisterRequest;
import com.ecomtest.entity.User;
import com.ecomtest.repository.UserRepository;
import com.ecomtest.security.CustomUserDetailsService;
import com.ecomtest.security.JwtService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CustomUserDetailsService userDetailsService;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       CustomUserDetailsService userDetailsService,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userDetailsService = userDetailsService;
        this.jwtService = jwtService;
    }

    public User register(RegisterRequest request) {
        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        return userRepository.save(user);
    }

    public AuthResponse login(LoginRequest request) {
        UserDetails userDetails;
        try {
            userDetails = userDetailsService.loadUserByUsername(request.getEmail());
        } catch (UsernameNotFoundException e) {
            throw new BadCredentialsException("Invalid email or password");
        }

        if (!passwordEncoder.matches(request.getPassword(), userDetails.getPassword())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        AuthResponse response = new AuthResponse();
        response.setToken(jwtService.generateToken(user.getEmail()));
        response.setId(user.getId());
        response.setEmail(user.getEmail());
        return response;
    }
}
