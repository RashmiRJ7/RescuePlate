package com.foodrescue.algorithm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.foodrescue.entity.Donation;
import com.foodrescue.enums.FoodCategory;

class UrgencyScoreCalculatorTest {

    private UrgencyScoreCalculator calculator;
    private final Instant now = Instant.parse("2026-11-20T12:00:00Z");

    @BeforeEach
    void setUp() {
        calculator = new UrgencyScoreCalculator();
    }

    @Test
    void benchmarkExampleA_CookedMeal_30Min_Qty50_Scores95() {
        Donation donation = new Donation();
        donation.setCategory(FoodCategory.COOKED_MEAL);
        donation.setQuantity(50);
        donation.setSafeUntil(now.plus(30, ChronoUnit.MINUTES));

        double score = calculator.calculate(donation, now);

        // 0.60 * 100 + 0.30 * 100 + 0.10 * 50 = 60 + 30 + 5 = 95.0
        assertEquals(95.0, score, 0.01);
    }

    @Test
    void benchmarkExampleB_CookedMeal_180Min_Qty40_Scores66Point7() {
        Donation donation = new Donation();
        donation.setCategory(FoodCategory.COOKED_MEAL);
        donation.setQuantity(40);
        donation.setSafeUntil(now.plus(180, ChronoUnit.MINUTES));

        double score = calculator.calculate(donation, now);

        // timeScore = 100 * (360 - 180) / 330 = 54.545
        // urgency = 0.60 * 54.545 + 0.30 * 100 + 0.10 * 40 = 32.727 + 30 + 4 = 66.727 -> 66.7
        assertEquals(66.7, score, 0.01);
    }

    @Test
    void benchmarkExampleC_Packaged_300Min_Qty100_Scores26Point9() {
        Donation donation = new Donation();
        donation.setCategory(FoodCategory.PACKAGED);
        donation.setQuantity(100);
        donation.setSafeUntil(now.plus(300, ChronoUnit.MINUTES));

        double score = calculator.calculate(donation, now);

        // timeScore = 100 * (360 - 300) / 330 = 18.1818
        // urgency = 0.60 * 18.182 + 0.30 * 20 + 0.10 * 100 = 10.909 + 6 + 10 = 26.909 -> 26.9
        assertEquals(26.9, score, 0.01);
    }

    @Test
    void benchmarkExampleD_Bakery_310Min_Qty30_Scores30Point1() {
        Donation donation = new Donation();
        donation.setCategory(FoodCategory.BAKERY);
        donation.setQuantity(30);
        donation.setSafeUntil(now.plus(310, ChronoUnit.MINUTES));

        double score = calculator.calculate(donation, now);

        // timeScore = 100 * (360 - 310) / 330 = 15.1515
        // urgency = 0.60 * 15.1515 + 0.30 * 60 + 0.10 * 30 = 9.091 + 18.0 + 3.0 = 30.091 -> 30.1
        assertEquals(30.1, score, 0.01);
    }

    @Test
    void timeScore_20MinLeft_ScoresMax100() {
        assertEquals(100.0, calculator.calculateTimeScore(20));
    }

    @Test
    void timeScore_400MinLeft_ScoresZero() {
        assertEquals(0.0, calculator.calculateTimeScore(400));
    }

    @Test
    void timeScore_195MinLeft_ScoresExactly50() {
        // (360 - 195) = 165; 165 / 330 = 0.5 -> 50.0
        assertEquals(50.0, calculator.calculateTimeScore(195), 0.01);
    }

    @Test
    void cookedVsPackaged_SameTimeAndQuantity_CookedScoresHigher() {
        Donation cooked = new Donation();
        cooked.setCategory(FoodCategory.COOKED_MEAL);
        cooked.setQuantity(30);
        cooked.setSafeUntil(now.plus(120, ChronoUnit.MINUTES));

        Donation packaged = new Donation();
        packaged.setCategory(FoodCategory.PACKAGED);
        packaged.setQuantity(30);
        packaged.setSafeUntil(now.plus(120, ChronoUnit.MINUTES));

        double cookedScore = calculator.calculate(cooked, now);
        double packagedScore = calculator.calculate(packaged, now);

        assertTrue(cookedScore > packagedScore);
    }

    @Test
    void quantityScore_CapsAt100() {
        assertEquals(100.0, calculator.calculateQuantityScore(500));
        assertEquals(45.0, calculator.calculateQuantityScore(45));
    }

    @Test
    void expiredDonation_ReturnsZero() {
        Donation expired = new Donation();
        expired.setCategory(FoodCategory.COOKED_MEAL);
        expired.setQuantity(50);
        expired.setSafeUntil(now.minus(5, ChronoUnit.MINUTES));

        assertEquals(0.0, calculator.calculate(expired, now));
    }
}
