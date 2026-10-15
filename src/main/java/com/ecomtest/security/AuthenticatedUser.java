package com.ecomtest.security;

import com.ecomtest.entity.Role;

public record AuthenticatedUser(Long id, String username, Role role) {
}
