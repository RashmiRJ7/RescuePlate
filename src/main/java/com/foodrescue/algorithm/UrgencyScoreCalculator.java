package com.foodrescue.algorithm;

import java.time.Duration;
import java.time.Instant;

import com.foodrescue.config.ScoringProperties;
import com.foodrescue.entity.Donation;
import com.foodrescue.enums.FoodCategory;

/**
 * Pure Java calculator for Donation Urgency Score (0 - 100).
 * Decoupled from Spring and databases for instant, deterministic testing.
 *
 * Formula: urgency = 0.60 * timeScore + 0.30 * perishabilityScore + 0.10 * quantityScore
 */
public class UrgencyScoreCalculator {

    private final double weightTime;
    private final double weightPerishability;
    private final double weightQuantity;
    private final long timeFullUrgencyMinutes;
    private final long timeZeroUrgencyMinutes;
    private final int quantityCap;

    public UrgencyScoreCalculator() {
        this(new ScoringProperties.Urgency());
    }

    public UrgencyScoreCalculator(ScoringProperties.Urgency props) {
        this.weightTime = props.getWeightTime();
        this.weightPerishability = props.getWeightPerishability();
        this.weightQuantity = props.getWeightQuantity();
        this.timeFullUrgencyMinutes = props.getTimeFullUrgencyMinutes();
        this.timeZeroUrgencyMinutes = props.getTimeZeroUrgencyMinutes();
        this.quantityCap = props.getQuantityCap();
    }

    /**
     * Computes the urgency score for a donation at a given instant.
     * Returns a score between 0.0 and 100.0 rounded to one decimal place.
     */
    public double calculate(Donation donation, Instant now) {
        if (donation == null || donation.getSafeUntil() == null) {
            return 0.0;
        }

        long minutesLeft = calculateMinutesLeft(donation.getSafeUntil(), now);
        if (minutesLeft <= 0) {
            return 0.0; // Expired
        }

        double timeScore = calculateTimeScore(minutesLeft);
        double perishabilityScore = calculatePerishabilityScore(donation.getCategory());
        double quantityScore = calculateQuantityScore(donation.getQuantity());

        double rawUrgency = (weightTime * timeScore)
                + (weightPerishability * perishabilityScore)
                + (weightQuantity * quantityScore);

        // Clamp between 0.0 and 100.0 and round to 1 decimal place
        double clamped = Math.max(0.0, Math.min(100.0, rawUrgency));
        return Math.round(clamped * 10.0) / 10.0;
    }

    public long calculateMinutesLeft(Instant safeUntil, Instant now) {
        if (safeUntil == null || now == null) {
            return 0;
        }
        return Math.max(0, Duration.between(now, safeUntil).toMinutes());
    }

    public double calculateTimeScore(long minutesLeft) {
        if (minutesLeft <= timeFullUrgencyMinutes) {
            return 100.0;
        }
        if (minutesLeft >= timeZeroUrgencyMinutes) {
            return 0.0;
        }
        return 100.0 * (timeZeroUrgencyMinutes - minutesLeft) / (timeZeroUrgencyMinutes - timeFullUrgencyMinutes);
    }

    public double calculatePerishabilityScore(FoodCategory category) {
        if (category == null) {
            return 0.0;
        }
        return switch (category) {
            case COOKED_MEAL -> 100.0;
            case BAKERY, FRESH_PRODUCE -> 60.0;
            case PACKAGED -> 20.0;
        };
    }

    public double calculateQuantityScore(int quantity) {
        return Math.min(Math.max(0, quantity), quantityCap);
    }
}
