package com.foodrescue.algorithm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.foodrescue.entity.Donation;
import com.foodrescue.enums.FoodCategory;

class DonationFeedComparatorTest {

    private UrgencyScoreCalculator calculator;
    private final Instant now = Instant.parse("2026-11-20T12:00:00Z");

    @BeforeEach
    void setUp() {
        calculator = new UrgencyScoreCalculator();
    }

    private Donation createDonation(Long id, FoodCategory category, Integer quantity, Instant safeUntil) {
        Donation d = new Donation();
        d.setId(id);
        d.setCategory(category);
        d.setQuantity(quantity);
        d.setSafeUntil(safeUntil);
        return d;
    }

    @Test
    void clearUrgencyDifference_OrdersHighestUrgencyFirst() {
        // High urgency: cooked meal expiring in 30 min (95.0)
        Donation d1 = createDonation(1L, FoodCategory.COOKED_MEAL, 50, now.plus(30, ChronoUnit.MINUTES));
        // Low urgency: packaged food expiring in 300 min (26.9)
        Donation d2 = createDonation(2L, FoodCategory.PACKAGED, 100, now.plus(300, ChronoUnit.MINUTES));

        List<Donation> list = new ArrayList<>(List.of(d2, d1));
        list.sort(DonationFeedComparator.forDonations(calculator, now));

        assertEquals(1L, list.get(0).getId());
        assertEquals(2L, list.get(1).getId());
    }

    @Test
    void tiedUrgency_DifferingOnlyAfterFirstDecimal_EarlierSafeUntilFirst() {
        // Design rule: two raw doubles differing only after the 1st decimal place (e.g. 95.04 vs 95.01)
        // must be treated as TIED (both round to 95.0). Earlier safeUntil breaks the tie.
        Donation dLater = createDonation(1L, FoodCategory.COOKED_MEAL, 50, now.plus(40, ChronoUnit.MINUTES));
        Donation dEarlier = createDonation(2L, FoodCategory.COOKED_MEAL, 50, now.plus(30, ChronoUnit.MINUTES));

        // Use ScoredDonation directly to supply raw doubles that differ only beyond the 1st decimal
        ScoredDonation s1 = new ScoredDonation(dLater, 95.04);
        ScoredDonation s2 = new ScoredDonation(dEarlier, 95.01);

        List<ScoredDonation> list = new ArrayList<>(List.of(s1, s2));
        list.sort(DonationFeedComparator.scoredComparator());

        // s2 has earlier safeUntil (30 min vs 40 min), so s2 must be first despite slightly smaller raw score
        assertEquals(2L, list.get(0).donation().getId());
        assertEquals(1L, list.get(1).donation().getId());
    }

    @Test
    void tiedUrgencyAndSafeUntil_LargerQuantityFirst() {
        Instant sameSafeUntil = now.plus(60, ChronoUnit.MINUTES);
        // Both Cooked Meal with identical time left and same score calculation except quantity
        // To keep urgency score tied, we can construct ScoredDonation directly with same score
        Donation smallerQty = createDonation(1L, FoodCategory.COOKED_MEAL, 50, sameSafeUntil);
        Donation largerQty = createDonation(2L, FoodCategory.COOKED_MEAL, 80, sameSafeUntil);

        ScoredDonation s1 = new ScoredDonation(smallerQty, 85.0);
        ScoredDonation s2 = new ScoredDonation(largerQty, 85.0);

        List<ScoredDonation> list = new ArrayList<>(List.of(s1, s2));
        list.sort(DonationFeedComparator.scoredComparator());

        // Larger quantity (80 > 50) must sort first
        assertEquals(2L, list.get(0).donation().getId());
        assertEquals(1L, list.get(1).donation().getId());
    }

    @Test
    void everythingTied_LowerIdFirst() {
        Instant sameSafeUntil = now.plus(60, ChronoUnit.MINUTES);
        Donation dHighId = createDonation(10L, FoodCategory.COOKED_MEAL, 50, sameSafeUntil);
        Donation dLowId = createDonation(3L, FoodCategory.COOKED_MEAL, 50, sameSafeUntil);

        ScoredDonation s1 = new ScoredDonation(dHighId, 85.0);
        ScoredDonation s2 = new ScoredDonation(dLowId, 85.0);

        List<ScoredDonation> list = new ArrayList<>(List.of(s1, s2));
        list.sort(DonationFeedComparator.scoredComparator());

        // Lower id (3 < 10) provides stable, deterministic ordering
        assertEquals(3L, list.get(0).donation().getId());
        assertEquals(10L, list.get(1).donation().getId());
    }

    @Test
    void quantity150Vs200_BothQuantityScore100_200ListedFirst() {
        // When quantity is capped at 100, both 150 and 200 get quantityScore = 100.
        // With otherwise equal inputs, both have identical urgency score (100.0) and safeUntil.
        // The 3rd tie-breaking level (batch size) ensures 200 is listed first.
        Instant sameSafeUntil = now.plus(20, ChronoUnit.MINUTES);
        Donation d150 = createDonation(2L, FoodCategory.COOKED_MEAL, 150, sameSafeUntil);
        Donation d200 = createDonation(1L, FoodCategory.COOKED_MEAL, 200, sameSafeUntil);

        double score150 = calculator.calculate(d150, now);
        double score200 = calculator.calculate(d200, now);

        assertEquals(100.0, score150);
        assertEquals(100.0, score200);

        List<Donation> list = new ArrayList<>(List.of(d150, d200));
        list.sort(DonationFeedComparator.forDonations(calculator, now));

        assertEquals(1L, list.get(0).getId()); // d200 first
        assertEquals(200, list.get(0).getQuantity());
        assertEquals(2L, list.get(1).getId()); // d150 second
        assertEquals(150, list.get(1).getQuantity());
    }

    @Test
    void existingWorkedExamples_MaintainExpectedScoresAndFeedOrder() {
        // Benchmark A: Cooked, 30 min, qty 50 -> 95.0
        Donation a = createDonation(1L, FoodCategory.COOKED_MEAL, 50, now.plus(30, ChronoUnit.MINUTES));
        // Benchmark B: Cooked, 180 min, qty 40 -> 66.7
        Donation b = createDonation(2L, FoodCategory.COOKED_MEAL, 40, now.plus(180, ChronoUnit.MINUTES));
        // Benchmark C: Packaged, 300 min, qty 100 -> 26.9
        Donation c = createDonation(3L, FoodCategory.PACKAGED, 100, now.plus(300, ChronoUnit.MINUTES));
        // Benchmark D: Bakery, 310 min, qty 30 -> 30.1
        Donation d = createDonation(4L, FoodCategory.BAKERY, 30, now.plus(310, ChronoUnit.MINUTES));

        assertEquals(95.0, calculator.calculate(a, now), 0.01);
        assertEquals(66.7, calculator.calculate(b, now), 0.01);
        assertEquals(26.9, calculator.calculate(c, now), 0.01);
        assertEquals(30.1, calculator.calculate(d, now), 0.01);

        List<Donation> feed = new ArrayList<>(List.of(c, d, b, a));
        feed.sort(DonationFeedComparator.forDonations(calculator, now));

        // Expected feed order: A (95.0) -> B (66.7) -> D (30.1) -> C (26.9)
        assertEquals(List.of(1L, 2L, 4L, 3L), feed.stream().map(Donation::getId).toList());
    }

    @Test
    void scoredDonationCarrier_ComparatorMatchesForDonations() {
        Donation a = createDonation(1L, FoodCategory.COOKED_MEAL, 50, now.plus(30, ChronoUnit.MINUTES));
        Donation b = createDonation(2L, FoodCategory.COOKED_MEAL, 40, now.plus(180, ChronoUnit.MINUTES));

        ScoredDonation sa = ScoredDonation.from(a, calculator, now);
        ScoredDonation sb = ScoredDonation.from(b, calculator, now);

        List<ScoredDonation> scoredList = new ArrayList<>(List.of(sb, sa));
        scoredList.sort(ScoredDonation.comparator());

        assertEquals(1L, scoredList.get(0).donation().getId());
        assertEquals(2L, scoredList.get(1).donation().getId());
    }
}
