package com.foodrescue.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.foodrescue.algorithm.UrgencyScoreCalculator;
import com.foodrescue.dto.DonationFeedResponse;
import com.foodrescue.entity.Donation;
import com.foodrescue.entity.NgoProfile;
import com.foodrescue.entity.User;
import com.foodrescue.enums.DonationStatus;
import com.foodrescue.enums.FoodCategory;
import com.foodrescue.enums.Role;
import com.foodrescue.repository.DonationRepository;
import com.foodrescue.repository.NgoProfileRepository;
import com.foodrescue.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class FeedServiceTest {

    @Mock
    private DonationRepository donationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NgoProfileRepository ngoProfileRepository;

    private UrgencyScoreCalculator urgencyCalculator;
    private FeedService feedService;

    private User ngoUser;
    private NgoProfile ngoProfile;
    private User donorUser;
    private Instant now;

    @BeforeEach
    void setUp() {
        urgencyCalculator = new UrgencyScoreCalculator();
        feedService = new FeedService(
                donationRepository,
                userRepository,
                ngoProfileRepository,
                urgencyCalculator
        );

        now = Instant.parse("2026-11-20T12:00:00Z");

        ngoUser = new User("City Shelter", "shelter@ngo.example", "hash", Role.NGO);
        ngoUser.setId(5L);
        ngoUser.setActive(true);
        ngoUser.setLatitude(15.8497);
        ngoUser.setLongitude(74.4977);

        ngoProfile = new NgoProfile(ngoUser, 50, 10.0, "COOKED_MEAL,BAKERY,PACKAGED,FRESH_PRODUCE");
        ngoProfile.setId(1L);

        donorUser = new User("Hotel Sunrise", "kitchen@sunrise.example", "hash", Role.DONOR);
        donorUser.setId(10L);
    }

    private Donation buildDonation(Long id, String name, FoodCategory cat, int qty,
                                   double lat, double lng, int minutesAhead) {
        Donation d = new Donation(
                donorUser,
                name,
                cat,
                qty,
                "Belagavi",
                lat,
                lng,
                now,
                now.plus(Duration.ofMinutes(minutesAhead))
        );
        d.setId(id);
        d.setStatus(DonationStatus.AVAILABLE);
        return d;
    }

    @Test
    void benchmarkOrdering_Cooked30m_Cooked180m_Packaged300m_Gives_95_66point7_26point9() {
        // Benchmark A: Cooked, 30 min, qty 50 -> 95.0
        Donation d1 = buildDonation(1L, "Biryani 30m", FoodCategory.COOKED_MEAL, 50, 15.8497, 74.4977, 30);
        // Benchmark B: Cooked, 180 min, qty 40 -> 66.7
        Donation d2 = buildDonation(2L, "Meals 180m", FoodCategory.COOKED_MEAL, 40, 15.8497, 74.4977, 180);
        // Benchmark C: Packaged, 300 min, qty 100 -> 26.9
        Donation d3 = buildDonation(3L, "Packaged 300m", FoodCategory.PACKAGED, 100, 15.8497, 74.4977, 300);

        when(userRepository.findByEmail("shelter@ngo.example")).thenReturn(Optional.of(ngoUser));
        when(ngoProfileRepository.findByUserId(5L)).thenReturn(Optional.of(ngoProfile));
        // Return unsorted from repository
        when(donationRepository.findByStatusInAndSafeUntilAfter(any(), any())).thenReturn(List.of(d3, d1, d2));

        List<DonationFeedResponse> feed = feedService.getFeed("shelter@ngo.example", null, null, null, 0, 20, now);

        assertEquals(3, feed.size());
        assertEquals(1L, feed.get(0).id());
        assertEquals(95.0, feed.get(0).urgencyScore());
        assertEquals(2L, feed.get(1).id());
        assertEquals(66.7, feed.get(1).urgencyScore());
        assertEquals(3L, feed.get(2).id());
        assertEquals(26.9, feed.get(2).urgencyScore());
    }

    @Test
    void changingAcceptedCategories_ShrinksFeed() {
        Donation cooked = buildDonation(1L, "Cooked", FoodCategory.COOKED_MEAL, 20, 15.8497, 74.4977, 60);
        Donation bakery = buildDonation(2L, "Bakery", FoodCategory.BAKERY, 20, 15.8497, 74.4977, 60);

        // NGO only accepts COOKED_MEAL
        ngoProfile.setAcceptedCategories("COOKED_MEAL");

        when(userRepository.findByEmail("shelter@ngo.example")).thenReturn(Optional.of(ngoUser));
        when(ngoProfileRepository.findByUserId(5L)).thenReturn(Optional.of(ngoProfile));
        when(donationRepository.findByStatusInAndSafeUntilAfter(any(), any())).thenReturn(List.of(cooked, bakery));

        List<DonationFeedResponse> feed = feedService.getFeed("shelter@ngo.example", null, null, null, 0, 20, now);

        assertEquals(1, feed.size());
        assertEquals(1L, feed.get(0).id());
        assertEquals(FoodCategory.COOKED_MEAL, feed.get(0).category());
    }

    @Test
    void distanceFiltering_HidesDonationsOutsideRadius() {
        // Location ~2 km away (inside 10 km radius)
        Donation near = buildDonation(1L, "Near Food", FoodCategory.COOKED_MEAL, 20, 15.86, 74.50, 60);
        // Location ~30 km away (outside 10 km radius)
        Donation far = buildDonation(2L, "Far Food", FoodCategory.COOKED_MEAL, 20, 16.10, 74.70, 60);

        when(userRepository.findByEmail("shelter@ngo.example")).thenReturn(Optional.of(ngoUser));
        when(ngoProfileRepository.findByUserId(5L)).thenReturn(Optional.of(ngoProfile));
        when(donationRepository.findByStatusInAndSafeUntilAfter(any(), any())).thenReturn(List.of(near, far));

        List<DonationFeedResponse> feed = feedService.getFeed("shelter@ngo.example", null, null, null, 0, 20, now);

        assertEquals(1, feed.size());
        assertEquals(1L, feed.get(0).id());
    }

    @Test
    void maxDistanceFilter_CanNarrowRadius_ButNotWiden() {
        Donation at2km = buildDonation(1L, "2 km", FoodCategory.COOKED_MEAL, 20, 15.86, 74.50, 60);

        when(userRepository.findByEmail("shelter@ngo.example")).thenReturn(Optional.of(ngoUser));
        when(ngoProfileRepository.findByUserId(5L)).thenReturn(Optional.of(ngoProfile));
        when(donationRepository.findByStatusInAndSafeUntilAfter(any(), any())).thenReturn(List.of(at2km));

        // Narrowing to 1 km excludes the 2 km donation
        List<DonationFeedResponse> feedNarrowed = feedService.getFeed("shelter@ngo.example", null, null, 1.0, 0, 20, now);
        assertEquals(0, feedNarrowed.size());

        // Asking for 20 km is capped at NGO's 10 km radius
        List<DonationFeedResponse> feedWidenedRequest = feedService.getFeed("shelter@ngo.example", null, null, 20.0, 0, 20, now);
        assertEquals(1, feedWidenedRequest.size());
    }

    @Test
    void minQuantityFilter_FiltersCorrectly() {
        Donation small = buildDonation(1L, "Small", FoodCategory.COOKED_MEAL, 10, 15.8497, 74.4977, 60);
        Donation large = buildDonation(2L, "Large", FoodCategory.COOKED_MEAL, 100, 15.8497, 74.4977, 60);

        when(userRepository.findByEmail("shelter@ngo.example")).thenReturn(Optional.of(ngoUser));
        when(ngoProfileRepository.findByUserId(5L)).thenReturn(Optional.of(ngoProfile));
        when(donationRepository.findByStatusInAndSafeUntilAfter(any(), any())).thenReturn(List.of(small, large));

        List<DonationFeedResponse> feed = feedService.getFeed("shelter@ngo.example", null, 50, null, 0, 20, now);

        assertEquals(1, feed.size());
        assertEquals(2L, feed.get(0).id());
    }

    @Test
    void exceedsCapacityFlag_IsTrueWhenQuantityExceedsCapacityMeals() {
        ngoProfile.setCapacityMeals(50);

        Donation withinCapacity = buildDonation(1L, "Within", FoodCategory.COOKED_MEAL, 50, 15.8497, 74.4977, 60);
        Donation exceeds = buildDonation(2L, "Exceeds", FoodCategory.COOKED_MEAL, 80, 15.8497, 74.4977, 60);

        when(userRepository.findByEmail("shelter@ngo.example")).thenReturn(Optional.of(ngoUser));
        when(ngoProfileRepository.findByUserId(5L)).thenReturn(Optional.of(ngoProfile));
        when(donationRepository.findByStatusInAndSafeUntilAfter(any(), any())).thenReturn(List.of(withinCapacity, exceeds));

        List<DonationFeedResponse> feed = feedService.getFeed("shelter@ngo.example", null, null, null, 0, 20, now);

        assertFalse(feed.stream().filter(f -> f.id().equals(1L)).findFirst().get().exceedsCapacity());
        assertTrue(feed.stream().filter(f -> f.id().equals(2L)).findFirst().get().exceedsCapacity());
    }

    @Test
    void pagination_ReturnsSlices() {
        Donation d1 = buildDonation(1L, "A", FoodCategory.COOKED_MEAL, 50, 15.8497, 74.4977, 30);
        Donation d2 = buildDonation(2L, "B", FoodCategory.COOKED_MEAL, 40, 15.8497, 74.4977, 180);
        Donation d3 = buildDonation(3L, "C", FoodCategory.PACKAGED, 100, 15.8497, 74.4977, 300);

        when(userRepository.findByEmail("shelter@ngo.example")).thenReturn(Optional.of(ngoUser));
        when(ngoProfileRepository.findByUserId(5L)).thenReturn(Optional.of(ngoProfile));
        when(donationRepository.findByStatusInAndSafeUntilAfter(any(), any())).thenReturn(List.of(d1, d2, d3));

        List<DonationFeedResponse> page0 = feedService.getFeed("shelter@ngo.example", null, null, null, 0, 2, now);
        assertEquals(2, page0.size());
        assertEquals(1L, page0.get(0).id());
        assertEquals(2L, page0.get(1).id());

        List<DonationFeedResponse> page1 = feedService.getFeed("shelter@ngo.example", null, null, null, 1, 2, now);
        assertEquals(1, page1.size());
        assertEquals(3L, page1.get(0).id());
    }

    @Test
    void inactiveNgo_ReturnsEmptyList() {
        ngoUser.setActive(false);

        when(userRepository.findByEmail("shelter@ngo.example")).thenReturn(Optional.of(ngoUser));

        List<DonationFeedResponse> feed = feedService.getFeed("shelter@ngo.example", null, null, null, 0, 20, now);

        assertTrue(feed.isEmpty());
    }
}
