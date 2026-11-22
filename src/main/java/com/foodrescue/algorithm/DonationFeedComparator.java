package com.foodrescue.algorithm;

import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;

import com.foodrescue.entity.Donation;

/**
 * Pure Java comparator implementing the 4-level tie-breaking feed ordering rule:
 * <ol>
 *   <li>Urgency score, highest first (scores equal after rounding to 1 decimal place are treated as tied)</li>
 *   <li>If urgency is tied: earlier {@code safeUntil} first</li>
 *   <li>If still tied: larger {@code quantity} first</li>
 *   <li>If still tied: lower donation {@code id} first (stable, deterministic order)</li>
 * </ol>
 * <p>
 * Decoupled from Spring and database layers. Time is injectable so all tests remain deterministic.
 * </p>
 */
public final class DonationFeedComparator {

    private static final Comparator<ScoredDonation> SCORED_COMPARATOR =
            Comparator.<ScoredDonation>comparingLong(s -> Math.round(s.urgencyScore() * 10.0)).reversed()
                    .thenComparing(s -> s.donation().getSafeUntil(), Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(s -> s.donation().getQuantity(), Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(s -> s.donation().getId(), Comparator.nullsLast(Comparator.naturalOrder()));

    private DonationFeedComparator() {
        // Utility class
    }

    /**
     * Returns the 4-level comparator for pre-scored {@link ScoredDonation} instances.
     * Recommended for feed sorting to avoid re-scoring $O(N \log N)$ times.
     */
    public static Comparator<ScoredDonation> scoredComparator() {
        return SCORED_COMPARATOR;
    }

    /**
     * Creates a comparator for raw {@link Donation} entities using the provided calculator and reference instant.
     * Null donations sort last.
     *
     * @param calculator the urgency score calculator
     * @param now        the reference instant for time calculations
     * @return a comparator ordering donations according to the 4-level feed priority
     */
    public static Comparator<Donation> forDonations(UrgencyScoreCalculator calculator, Instant now) {
        Objects.requireNonNull(calculator, "calculator must not be null");
        Objects.requireNonNull(now, "now must not be null");
        return Comparator.nullsLast((d1, d2) -> {
            ScoredDonation s1 = ScoredDonation.from(d1, calculator, now);
            ScoredDonation s2 = ScoredDonation.from(d2, calculator, now);
            return SCORED_COMPARATOR.compare(s1, s2);
        });
    }
}
