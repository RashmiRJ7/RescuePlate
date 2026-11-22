package com.foodrescue.dto;

import com.foodrescue.enums.Role;

public record UserProfileResponse(
    Long id,
    String name,
    String email,
    Role role,
    Double latitude,
    Double longitude
) {}
