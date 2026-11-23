package com.foodrescue.dto;

import com.foodrescue.enums.Role;

public record AdminUserResponse(
    Long id,
    String name,
    String email,
    Role role,
    boolean active,
    String address,
    Double latitude,
    Double longitude,
    String createdAt
) {}
