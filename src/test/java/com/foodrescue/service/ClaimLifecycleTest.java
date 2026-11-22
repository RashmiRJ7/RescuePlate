package com.foodrescue.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.foodrescue.algorithm.UrgencyScoreCalculator;
import com.foodrescue.config.ScoringProperties;
import com.foodrescue.dto.ClaimResponse;
import com.foodrescue.dto.DonationFeedResponse;
import com.foodrescue.entity.Claim;
import com.foodrescue.entity.Donation;
import com.foodrescue.entity.NgoProfile;
import com.foodrescue.entity.User;
import com.foodrescue.enums.ClaimStatus;
import com.foodrescue.enums.DonationStatus;
import com.foodrescue.enums.FoodCategory;
import com.foodrescue.enums.Role;
import com.foodrescue.repository.ClaimRepository;
import com.foodrescue.repository.DonationRepository;
import com.foodrescue.repository.DonationStatusHistoryRepository;
import com.foodrescue.repository.NgoProfileRepository;
import com.foodrescue.repository.UserRepository;

/**
 * Step 10 Lifecycle & Roadmap Verification Test:
 * "Cancel a claim, confirm the donation reappears in the feed, then claim it again with a different NGO."
 */
@ExtendWith(MockitoExtension.class)
class ClaimLifecycleTest {

    @Mock
    private DonationRepository donationRepository;

    @Mock
    private ClaimRepository claimRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NgoProfileRepository ngoProfileRepository;

    @Mock
    private DonationStatusHistoryRepository historyRepository;

    private DonationService donationService;
    private ClaimService claimService;
    private FeedService feedService;

    private User donor;
    private User ngo1;
    private User ngo2;
    private NgoProfile ngoProfile1;
    private NgoProfile ngoProfile2;
    private Donation donation;
    private Instant testNow;

    private final List<Claim> storedClaims = new ArrayList<>();

    @BeforeEach
    void setUp() {
        testNow = Instant.parse("2026-11-20T12:00:00Z");

        donationService = new DonationService(donationRepository, historyRepository, claimRepository, userRepository);
        claimService = new ClaimService(donationRepository, claimRepository, userRepository, ngoProfileRepository, donationService);

        ScoringProperties scoringProperties = new ScoringProperties();
        feedService = new FeedService(donationRepository, userRepository, ngoProfileRepository, scoringProperties);

        donor = new User("Hotel Paradise", "donor@example.com", "hash", Role.DONOR);
        donor.setId(1L);

        ngo1 = new User("Care Foundation", "ngo1@example.com", "hash", Role.NGO);
        ngo1.setId(10L);
        ngo1.setActive(true);
        ngo1.setLatitude(15.8520);
        ngo1.setLongitude(74.5020);

        ngoProfile1 = new NgoProfile(ngo1, 100, 10.0, "COOKED_MEAL,PACKAGED");
        ngoProfile1.setId(11L);

        ngo2 = new User("Hope Shelter", "ngo2@example.com", "hash", Role.NGO);
        ngo2.setId(20L);
        ngo2.setActive(true);
        ngo2.setLatitude(15.8530);
        ngo2.setLongitude(74.5030);

        ngoProfile2 = new NgoProfile(ngo2, 100, 10.0, "COOKED_MEAL,PACKAGED");
        ngoProfile2.setId(21L);

        donation = new Donation();
        donation.setId(100L);
        donation.setDonor(donor);
        donation.setFoodName("Veg Biryani");
        donation.setCategory(FoodCategory.COOKED_MEAL);
        donation.setQuantity(60);
        donation.setSafeUntil(testNow.plusSeconds(7200)); // 2 hours
        donation.setPickupAddress("College Road, Belagavi");
        donation.setLatitude(15.8500);
        donation.setLongitude(74.5000);
        donation.setStatus(DonationStatus.AVAILABLE);

        when(userRepository.findByEmail("ngo1@example.com")).thenReturn(Optional.of(ngo1));
        when(userRepository.findByEmail("ngo2@example.com")).thenReturn(Optional.of(ngo2));
        when(ngoProfileRepository.findByUserId(10L)).thenReturn(Optional.of(ngoProfile1));
        when(ngoProfileRepository.findByUserId(20L)).thenReturn(Optional.of(ngoProfile2));

        when(donationRepository.findById(100L)).thenReturn(Optional.of(donation));
        when(donationRepository.save(any(Donation.class))).thenAnswer(i -> i.getArgument(0));
        when(donationRepository.saveAndFlush(any(Donation.class))).thenAnswer(i -> i.getArgument(0));

        when(claimRepository.save(any(Claim.class))).thenAnswer(i -> {
            Claim c = i.getArgument(0);
            if (c.getId() == null) {
                c.setId((long) (storedClaims.size() + 1));
                storedClaims.add(c);
            }
            return c;
        });
        when(claimRepository.saveAndFlush(any(Claim.class))).thenAnswer(i -> {
            Claim c = i.getArgument(0);
            if (c.getId() == null) {
                c.setId((long) (storedClaims.size() + 1));
                storedClaims.add(c);
            }
            return c;
        });
        when(claimRepository.findByActiveDonationId(100L)).thenAnswer(i ->
                storedClaims.stream()
                        .filter(c -> Long.valueOf(100L).equals(c.getActiveDonationId()))
                        .findFirst()
        );
        when(claimRepository.findById(any(Long.class))).thenAnswer(i -> {
            Long id = i.getArgument(0);
            return storedClaims.stream().filter(c -> id.equals(c.getId())).findFirst();
        });
    }

    @Test
    @DisplayName("Complete claim lifecycle: Claim -> Feed disappears -> Cancel -> Feed reappears -> Claim by another NGO -> Pickup -> Deliver")
    void completeLifecycle_cancelReopensDonationForOtherNgo() {
        // Step A: Initial state - donation is AVAILABLE. Mock feed candidate query.
        when(donationRepository.findByStatusInAndSafeUntilAfter(any(), eq(testNow)))
                .thenAnswer(i -> donation.getStatus() == DonationStatus.AVAILABLE ? List.of(donation) : Collections.emptyList());

        List<DonationFeedResponse> feedBeforeClaim = feedService.getFeed("ngo2@example.com", null, null, null, null, null, testNow);
        assertThat(feedBeforeClaim).hasSize(1);
        assertThat(feedBeforeClaim.get(0).id()).isEqualTo(100L);

        // Step B: NGO 1 claims the donation
        ClaimResponse claim1Response = claimService.claim(100L, "ngo1@example.com", testNow);
        assertThat(claim1Response).isNotNull();
        assertThat(claim1Response.status()).isEqualTo(ClaimStatus.ACTIVE);
        assertThat(donation.getStatus()).isEqualTo(DonationStatus.RESERVED);

        // Step C: NGO 2 checks feed -> donation is RESERVED so it no longer appears in feed
        List<DonationFeedResponse> feedDuringReservation = feedService.getFeed("ngo2@example.com", null, null, null, null, null, testNow);
        assertThat(feedDuringReservation).isEmpty();

        // Step D: NGO 1 cancels the claim
        ClaimResponse cancelResponse = claimService.cancelClaim(claim1Response.id(), "ngo1@example.com", "Driver unavailable", testNow);
        assertThat(cancelResponse.status()).isEqualTo(ClaimStatus.CANCELLED);
        assertThat(donation.getStatus()).isEqualTo(DonationStatus.AVAILABLE);

        // Step E: NGO 2 checks feed again -> donation REAPPEARS in the feed!
        List<DonationFeedResponse> feedAfterCancel = feedService.getFeed("ngo2@example.com", null, null, null, null, null, testNow);
        assertThat(feedAfterCancel).hasSize(1);
        assertThat(feedAfterCancel.get(0).id()).isEqualTo(100L);

        // Step F: NGO 2 claims the reopened donation
        ClaimResponse claim2Response = claimService.claim(100L, "ngo2@example.com", testNow);
        assertThat(claim2Response).isNotNull();
        assertThat(claim2Response.status()).isEqualTo(ClaimStatus.ACTIVE);
        assertThat(claim2Response.id()).isNotEqualTo(claim1Response.id());
        assertThat(donation.getStatus()).isEqualTo(DonationStatus.RESERVED);

        // Step G: NGO 2 picks up the donation
        ClaimResponse pickupResponse = claimService.pickupClaim(claim2Response.id(), "ngo2@example.com", testNow.plusSeconds(1800));
        assertThat(pickupResponse.status()).isEqualTo(ClaimStatus.PICKED_UP);
        assertThat(donation.getStatus()).isEqualTo(DonationStatus.PICKED_UP);

        // Step H: NGO 2 marks the donation as delivered
        ClaimResponse deliverResponse = claimService.deliverClaim(claim2Response.id(), "ngo2@example.com", testNow.plusSeconds(3600));
        assertThat(deliverResponse.status()).isEqualTo(ClaimStatus.COMPLETED);
        assertThat(donation.getStatus()).isEqualTo(DonationStatus.DELIVERED);

        // Step I: NGO 2 checks their claims
        when(claimRepository.findByNgoIdOrderByClaimedAtDesc(20L))
                .thenReturn(storedClaims.stream().filter(c -> c.getNgo().getId().equals(20L)).toList());

        List<ClaimResponse> ngo2Claims = claimService.getMyClaims("ngo2@example.com", null);
        assertThat(ngo2Claims).hasSize(1);
        assertThat(ngo2Claims.get(0).id()).isEqualTo(claim2Response.id());
        assertThat(ngo2Claims.get(0).status()).isEqualTo(ClaimStatus.COMPLETED);
    }
}
