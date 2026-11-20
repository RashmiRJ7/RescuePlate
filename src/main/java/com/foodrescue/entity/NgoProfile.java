package com.foodrescue.entity;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import com.foodrescue.enums.FoodCategory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "ngo_profiles")
public class NgoProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "capacity_meals", nullable = false)
    private Integer capacityMeals;

    @Column(name = "service_radius_km", nullable = false)
    private Double serviceRadiusKm;

    @Column(name = "accepted_categories", nullable = false, length = 255)
    private String acceptedCategories;

    @Column(name = "total_claims", nullable = false)
    private int totalClaims = 0;

    @Column(name = "completed_claims", nullable = false)
    private int completedClaims = 0;

    public NgoProfile() {
    }

    public NgoProfile(User user, Integer capacityMeals, Double serviceRadiusKm, String acceptedCategories) {
        this.user = user;
        this.capacityMeals = capacityMeals;
        this.serviceRadiusKm = serviceRadiusKm;
        this.acceptedCategories = acceptedCategories;
    }

    public Set<FoodCategory> getAcceptedCategoriesSet() {
        if (acceptedCategories == null || acceptedCategories.isBlank()) {
            return Collections.emptySet();
        }
        return Arrays.stream(acceptedCategories.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(FoodCategory::valueOf)
                .collect(Collectors.toSet());
    }

    public void setAcceptedCategoriesSet(Set<FoodCategory> categories) {
        if (categories == null || categories.isEmpty()) {
            this.acceptedCategories = "";
        } else {
            this.acceptedCategories = categories.stream()
                    .map(Enum::name)
                    .collect(Collectors.joining(","));
        }
    }

    public boolean acceptsCategory(FoodCategory category) {
        if (category == null) {
            return false;
        }
        return getAcceptedCategoriesSet().contains(category);
    }

    public Double getReliabilityPercent() {
        if (totalClaims <= 0) {
            return null;
        }
        return (double) completedClaims / totalClaims * 100.0;
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Integer getCapacityMeals() {
        return capacityMeals;
    }

    public void setCapacityMeals(Integer capacityMeals) {
        this.capacityMeals = capacityMeals;
    }

    public Double getServiceRadiusKm() {
        return serviceRadiusKm;
    }

    public void setServiceRadiusKm(Double serviceRadiusKm) {
        this.serviceRadiusKm = serviceRadiusKm;
    }

    public String getAcceptedCategories() {
        return acceptedCategories;
    }

    public void setAcceptedCategories(String acceptedCategories) {
        this.acceptedCategories = acceptedCategories;
    }

    public int getTotalClaims() {
        return totalClaims;
    }

    public void setTotalClaims(int totalClaims) {
        this.totalClaims = totalClaims;
    }

    public int getCompletedClaims() {
        return completedClaims;
    }

    public void setCompletedClaims(int completedClaims) {
        this.completedClaims = completedClaims;
    }
}
