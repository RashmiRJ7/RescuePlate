package com.foodrescue.algorithm;

import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;

import com.foodrescue.entity.Donation;

/**
 * Immutable carrier pairing a {@link Donation} with its computed urgency score.
 * <p>
 * Precomputing the urgency score prevents repeated $O(N \log N)$ recalculations
 * during feed sorting while keeping the comparator pure and decoupled from Spring/DB.
 * </p>
 */
public record ScoredDonation(Donation donation, double urgencyScore) {

    public ScoredDonation {
        Objects.requireNonNull(donation, "donation must not be null");
    }

    /**
     * Convenience factory to score a donation at a given instant.
     *
     * @param donation   the donation entity to score
     * @param calculator the urgency score calculator
     * @param now        the reference instant for time calculations
     * @return a new ScoredDonation carrier
     */
    public static ScoredDonation from(Donation donation, UrgencyScoreCalculator calculator, Instant now) {
        Objects.requireNonNull(calculator, "calculator must not be null");
        Objects.requireNonNull(now, "now must not be null");
        return new ScoredDonation(donation, calculator.calculate(donation, now));
    }

    /**
     * Returns the 4-level feed comparator for {@link ScoredDonation} instances:
     * <ol>
     *   <li>Urgency score descending (rounded to 1 decimal place)</li>
     *   <li>Earlier safeUntil ascending</li>
     *   <li>Larger quantity descending</li>
     *   <li>Lower donation id ascending (stable, deterministic order)</li>
     * </ol>
     */
    public static Comparator<ScoredDonation> comparator() {
        return DonationFeedComparator.scoredComparator();
    }
}
