package com.foodrescue.dto;

import java.time.Instant;

import com.foodrescue.enums.FoodCategory;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateDonationRequest(
    @NotBlank(message = "Food name is required")
    @Size(min = 2, max = 100, message = "Food name must be between 2 and 100 characters")
    String foodName,

    @NotNull(message = "Category is required")
    FoodCategory category,

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    @Max(value = 1000, message = "Quantity must be at most 1000")
    Integer quantity,

    @Size(max = 500, message = "Description must not exceed 500 characters")
    String description,

    @NotBlank(message = "Pickup address is required")
    String pickupAddress,

    @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
    @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
    Double latitude,

    @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
    @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
    Double longitude,

    Instant readyFrom,

    @NotNull(message = "Safe until time is required")
    Instant safeUntil
) {}
