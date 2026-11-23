package com.foodrescue.dto;

import java.util.Set;

import com.foodrescue.enums.FoodCategory;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record NgoProfileUpdateRequest(
    @NotNull(message = "Capacity meals is required")
    @Min(value = 1, message = "Capacity meals must be at least 1")
    Integer capacityMeals,

    @NotNull(message = "Service radius is required")
    @DecimalMin(value = "0.1", message = "Service radius must be greater than 0")
    Double serviceRadiusKm,

    @NotEmpty(message = "Accepted categories must not be empty")
    Set<FoodCategory> acceptedCategories,

    @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
    @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
    Double latitude,

    @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
    @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
    Double longitude
) {
    public NgoProfileUpdateRequest(Integer capacityMeals,
                                   Double serviceRadiusKm,
                                   Set<FoodCategory> acceptedCategories) {
        this(capacityMeals, serviceRadiusKm, acceptedCategories, null, null);
    }
}
