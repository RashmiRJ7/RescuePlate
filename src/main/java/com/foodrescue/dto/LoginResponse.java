package com.foodrescue.dto;

import com.foodrescue.enums.Role;

public record LoginResponse(
    Long id,
    String name,
    Role role
) {}
