package com.foodrescue.dto;

import com.foodrescue.enums.Role;

public record AuthUserResponse(
    Long id,
    String name,
    String email,
    Role role
) {}
