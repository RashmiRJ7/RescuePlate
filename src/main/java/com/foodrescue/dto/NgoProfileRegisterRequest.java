package com.foodrescue.dto;

import java.util.Set;

import com.foodrescue.enums.FoodCategory;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record NgoProfileRegisterRequest(
    @NotNull(message = "Capacity meals is required")
    @Min(value = 1, message = "Capacity meals must be at least 1")
    Integer capacityMeals,

    @NotNull(message = "Service radius is required")
    @DecimalMin(value = "0.1", message = "Service radius must be greater than 0")
    Double serviceRadiusKm,

    @NotEmpty(message = "At least one accepted category is required")
    Set<FoodCategory> acceptedCategories
) {}
