package com.foodrescue.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;

import com.foodrescue.dto.ClaimResponse;
import com.foodrescue.entity.Claim;
import com.foodrescue.entity.Donation;
import com.foodrescue.entity.NgoProfile;
import com.foodrescue.entity.User;
import com.foodrescue.enums.ClaimStatus;
import com.foodrescue.enums.DonationStatus;
import com.foodrescue.enums.FoodCategory;
import com.foodrescue.enums.Role;
import com.foodrescue.exception.DonationNotClaimableException;
import com.foodrescue.exception.NgoNotEligibleException;
import com.foodrescue.exception.ResourceNotFoundException;
import com.foodrescue.repository.ClaimRepository;
import com.foodrescue.repository.DonationRepository;
import com.foodrescue.repository.NgoProfileRepository;
import com.foodrescue.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class ClaimServiceTest {

    @Mock
    private DonationRepository donationRepository;

    @Mock
    private ClaimRepository claimRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NgoProfileRepository ngoProfileRepository;

    @Mock
    private DonationService donationService;

    @InjectMocks
    private ClaimService claimService;

    private Donation testDonation;
    private User testNgoUser;
    private NgoProfile testNgoProfile;
    private Instant testNow;

    @BeforeEach
    void setUp() {
        testNow = Instant.parse("2026-11-20T12:00:00Z");

        User donor = new User("Donor Hotel", "donor@example.com", "hash", Role.DONOR);
        donor.setPhone("1234567890");
        donor.setId(10L);

        testDonation = new Donation();
        testDonation.setId(1L);
        testDonation.setDonor(donor);
        testDonation.setFoodName("Veg Pulao");
        testDonation.setCategory(FoodCategory.COOKED_MEAL);
        testDonation.setQuantity(50);
        testDonation.setSafeUntil(testNow.plusSeconds(3600)); // 1 hr left
        testDonation.setPickupAddress("Club Road, Belagavi");
        testDonation.setLatitude(15.8500);
        testDonation.setLongitude(74.5000);
        testDonation.setStatus(DonationStatus.AVAILABLE);

        testNgoUser = new User("Helping Hands", "ngo@example.com", "hash", Role.NGO);
        testNgoUser.setPhone("9876543210");
        testNgoUser.setId(20L);
        testNgoUser.setActive(true);
        testNgoUser.setLatitude(15.8520);
        testNgoUser.setLongitude(74.5020); // ~0.3 km away

        testNgoProfile = new NgoProfile(testNgoUser, 40, 5.0, "COOKED_MEAL,PACKAGED");
        testNgoProfile.setId(30L);
    }

    @Test
    @DisplayName("claim: successful reservation when all conditions are met")
    void claim_success() {
        when(donationRepository.findById(1L)).thenReturn(Optional.of(testDonation));
        when(claimRepository.findByActiveDonationId(1L)).thenReturn(Optional.empty());
        when(userRepository.findByEmail("ngo@example.com")).thenReturn(Optional.of(testNgoUser));
        when(ngoProfileRepository.findByUserId(20L)).thenReturn(Optional.of(testNgoProfile));

        when(claimRepository.saveAndFlush(any(Claim.class))).thenAnswer(invocation -> {
            Claim c = invocation.getArgument(0);
            c.setId(100L);
            return c;
        });

        ClaimResponse response = claimService.claim(1L, "ngo@example.com", testNow);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.donationId()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo(ClaimStatus.ACTIVE);
        assertThat(response.claimedAt()).isEqualTo(testNow);
        assertThat(response.donation().id()).isEqualTo(1L);

        verify(donationService).changeStatus(eq(testDonation), eq(DonationStatus.RESERVED), eq(testNgoUser), any());
        verify(claimRepository).saveAndFlush(any(Claim.class));
        verify(donationRepository).saveAndFlush(testDonation);
    }

    @Test
    @DisplayName("claim: exceeding nominal capacity is allowed and succeeds")
    void claim_exceedingCapacity_succeeds() {
        testDonation.setQuantity(200); // Exceeds NGO capacity of 40

        when(donationRepository.findById(1L)).thenReturn(Optional.of(testDonation));
        when(claimRepository.findByActiveDonationId(1L)).thenReturn(Optional.empty());
        when(userRepository.findByEmail("ngo@example.com")).thenReturn(Optional.of(testNgoUser));
        when(ngoProfileRepository.findByUserId(20L)).thenReturn(Optional.of(testNgoProfile));

        when(claimRepository.saveAndFlush(any(Claim.class))).thenAnswer(invocation -> {
            Claim c = invocation.getArgument(0);
            c.setId(101L);
            return c;
        });

        ClaimResponse response = claimService.claim(1L, "ngo@example.com", testNow);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(101L);
        verify(donationService).changeStatus(eq(testDonation), eq(DonationStatus.RESERVED), eq(testNgoUser), any());
    }

    @Test
    @DisplayName("claim: 404 when donation not found")
    void claim_donationNotFound_throws404() {
        when(donationRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> claimService.claim(999L, "ngo@example.com", testNow))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Donation not found with id: 999");
    }

    @Test
    @DisplayName("claim: 409 when donation status is not claimable (e.g. RESERVED, DELIVERED)")
    void claim_notClaimableStatus_throws409() {
        testDonation.setStatus(DonationStatus.RESERVED);
        when(donationRepository.findById(1L)).thenReturn(Optional.of(testDonation));

        assertThatThrownBy(() -> claimService.claim(1L, "ngo@example.com", testNow))
                .isInstanceOf(DonationNotClaimableException.class)
                .hasMessageContaining("no longer available");
    }

    @Test
    @DisplayName("claim: 409 when donation is past safeUntil")
    void claim_pastSafeUntil_throws409() {
        testDonation.setSafeUntil(testNow.minusSeconds(60));
        when(donationRepository.findById(1L)).thenReturn(Optional.of(testDonation));

        assertThatThrownBy(() -> claimService.claim(1L, "ngo@example.com", testNow))
                .isInstanceOf(DonationNotClaimableException.class)
                .hasMessageContaining("no longer available");
    }

    @Test
    @DisplayName("claim: 409 when an active claim already exists on the donation")
    void claim_activeClaimExists_throws409() {
        when(donationRepository.findById(1L)).thenReturn(Optional.of(testDonation));
        when(claimRepository.findByActiveDonationId(1L)).thenReturn(Optional.of(new Claim(testDonation, testNgoUser)));

        assertThatThrownBy(() -> claimService.claim(1L, "ngo@example.com", testNow))
                .isInstanceOf(DonationNotClaimableException.class)
                .hasMessageContaining("no longer available");
    }

    @Test
    @DisplayName("claim: 400 when NGO user is inactive")
    void claim_inactiveNgo_throws400() {
        testNgoUser.setActive(false);
        when(donationRepository.findById(1L)).thenReturn(Optional.of(testDonation));
        when(claimRepository.findByActiveDonationId(1L)).thenReturn(Optional.empty());
        when(userRepository.findByEmail("ngo@example.com")).thenReturn(Optional.of(testNgoUser));

        assertThatThrownBy(() -> claimService.claim(1L, "ngo@example.com", testNow))
                .isInstanceOf(NgoNotEligibleException.class)
                .hasMessageContaining("NGO account is inactive");
    }

    @Test
    @DisplayName("claim: 400 when donation category is not accepted by NGO")
    void claim_categoryMismatch_throws400() {
        testDonation.setCategory(FoodCategory.FRESH_PRODUCE); // NGO only accepts COOKED_MEAL, PACKAGED

        when(donationRepository.findById(1L)).thenReturn(Optional.of(testDonation));
        when(claimRepository.findByActiveDonationId(1L)).thenReturn(Optional.empty());
        when(userRepository.findByEmail("ngo@example.com")).thenReturn(Optional.of(testNgoUser));
        when(ngoProfileRepository.findByUserId(20L)).thenReturn(Optional.of(testNgoProfile));

        assertThatThrownBy(() -> claimService.claim(1L, "ngo@example.com", testNow))
                .isInstanceOf(NgoNotEligibleException.class)
                .hasMessageContaining("NGO does not accept food category: FRESH_PRODUCE");
    }

    @Test
    @DisplayName("claim: 400 when donation pickup is beyond NGO service radius")
    void claim_distanceExceedsRadius_throws400() {
        // Place donation far away (~100km away)
        testDonation.setLatitude(16.5000);
        testDonation.setLongitude(75.5000);

        when(donationRepository.findById(1L)).thenReturn(Optional.of(testDonation));
        when(claimRepository.findByActiveDonationId(1L)).thenReturn(Optional.empty());
        when(userRepository.findByEmail("ngo@example.com")).thenReturn(Optional.of(testNgoUser));
        when(ngoProfileRepository.findByUserId(20L)).thenReturn(Optional.of(testNgoProfile));

        assertThatThrownBy(() -> claimService.claim(1L, "ngo@example.com", testNow))
                .isInstanceOf(NgoNotEligibleException.class)
                .hasMessageContaining("exceeds NGO service radius");
    }

    @Test
    @DisplayName("claim: OptimisticLockingFailureException during flush bubbles up for global handler")
    void claim_optimisticLockConflict_bubblesUp() {
        when(donationRepository.findById(1L)).thenReturn(Optional.of(testDonation));
        when(claimRepository.findByActiveDonationId(1L)).thenReturn(Optional.empty());
        when(userRepository.findByEmail("ngo@example.com")).thenReturn(Optional.of(testNgoUser));
        when(ngoProfileRepository.findByUserId(20L)).thenReturn(Optional.of(testNgoProfile));

        when(donationRepository.saveAndFlush(any(Donation.class)))
                .thenThrow(new OptimisticLockingFailureException("Version conflict"));

        assertThatThrownBy(() -> claimService.claim(1L, "ngo@example.com", testNow))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    @DisplayName("claim: DataIntegrityViolationException during flush bubbles up for global handler")
    void claim_uniqueConstraintViolation_bubblesUp() {
        when(donationRepository.findById(1L)).thenReturn(Optional.of(testDonation));
        when(claimRepository.findByActiveDonationId(1L)).thenReturn(Optional.empty());
        when(userRepository.findByEmail("ngo@example.com")).thenReturn(Optional.of(testNgoUser));
        when(ngoProfileRepository.findByUserId(20L)).thenReturn(Optional.of(testNgoProfile));

        when(claimRepository.saveAndFlush(any(Claim.class)))
                .thenThrow(new DataIntegrityViolationException("uq_claims_active_donation duplicate"));

        assertThatThrownBy(() -> claimService.claim(1L, "ngo@example.com", testNow))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
