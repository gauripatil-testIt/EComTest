package com.ecomtest.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/products/**").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/uploads/products/**").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/products/**").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/products/**").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/api/products/**").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/orders/**").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/orders/**").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/orders/**").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/api/orders/**").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(withDefaults -> {});
        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService(
            @Value("${app.security.admin.username}") String adminUsername,
            @Value("${app.security.admin.password}") String adminPassword,
            @Value("${app.security.staff.username}") String staffUsername,
            @Value("${app.security.staff.password}") String staffPassword,
            @Value("${app.security.customer.username}") String customerUsername,
            @Value("${app.security.customer.password}") String customerPassword,
            PasswordEncoder passwordEncoder) {
        var admin = User.withUsername(adminUsername)
                .password(passwordEncoder.encode(adminPassword))
                .roles("ADMIN")
                .build();
        var staff = User.withUsername(staffUsername)
                .password(passwordEncoder.encode(staffPassword))
                .roles("STAFF")
                .build();
        var customer = User.withUsername(customerUsername)
                .password(passwordEncoder.encode(customerPassword))
                .roles("CUSTOMER")
                .build();
        return new InMemoryUserDetailsManager(admin, staff, customer);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
