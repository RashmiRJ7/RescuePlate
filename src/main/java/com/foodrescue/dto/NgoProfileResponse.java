package com.foodrescue.dto;

import java.util.Set;

import com.foodrescue.entity.NgoProfile;
import com.foodrescue.enums.FoodCategory;

public record NgoProfileResponse(
    Integer capacityMeals,
    Double serviceRadiusKm,
    Set<FoodCategory> acceptedCategories,
    int totalClaims,
    int completedClaims,
    Double reliabilityPercent
) {
    public static NgoProfileResponse from(NgoProfile profile) {
        return new NgoProfileResponse(
            profile.getCapacityMeals(),
            profile.getServiceRadiusKm(),
            profile.getAcceptedCategoriesSet(),
            profile.getTotalClaims(),
            profile.getCompletedClaims(),
            profile.getReliabilityPercent()
        );
    }
}
