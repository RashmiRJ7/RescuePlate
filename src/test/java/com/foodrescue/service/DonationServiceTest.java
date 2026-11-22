package com.foodrescue.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.foodrescue.dto.CreateDonationRequest;
import com.foodrescue.dto.DonationHistoryResponse;
import com.foodrescue.dto.DonationResponse;
import com.foodrescue.entity.Claim;
import com.foodrescue.entity.Donation;
import com.foodrescue.entity.DonationStatusHistory;
import com.foodrescue.entity.User;
import com.foodrescue.enums.ClaimStatus;
import com.foodrescue.enums.DonationStatus;
import com.foodrescue.enums.FoodCategory;
import com.foodrescue.enums.Role;
import com.foodrescue.exception.InvalidStatusTransitionException;
import com.foodrescue.exception.ResourceNotFoundException;
import com.foodrescue.exception.ValidationException;
import com.foodrescue.repository.ClaimRepository;
import com.foodrescue.repository.DonationRepository;
import com.foodrescue.repository.DonationStatusHistoryRepository;
import com.foodrescue.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class DonationServiceTest {

    @Mock
    private DonationRepository donationRepository;

    @Mock
    private DonationStatusHistoryRepository historyRepository;

    @Mock
    private ClaimRepository claimRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private DonationService donationService;

    private User donorUser;
    private User ngoUser;

    @BeforeEach
    void setUp() {
        donorUser = new User();
        donorUser.setId(10L);
        donorUser.setName("Donor Hotel");
        donorUser.setRole(Role.DONOR);

        ngoUser = new User();
        ngoUser.setId(20L);
        ngoUser.setName("NGO Food Care");
        ngoUser.setRole(Role.NGO);
    }

    private Donation createDonation(Long id, DonationStatus status) {
        Donation d = new Donation();
        d.setId(id);
        d.setDonor(donorUser);
        d.setFoodName("Test Food");
        // Using reflection or testing hook - Donation constructor sets AVAILABLE
        // But for testing transitions, we set initial status directly
        d.setStatus(status);
        return d;
    }

    // -------------------------------------------------------------
    // Legal Transitions (15 valid moves)
    // -------------------------------------------------------------

    @ParameterizedTest
    @CsvSource({
            "AVAILABLE, DISPATCHING",
            "AVAILABLE, RESERVED",
            "AVAILABLE, EXPIRED",
            "AVAILABLE, CANCELLED",
            "DISPATCHING, AVAILABLE",
            "DISPATCHING, RESERVED",
            "DISPATCHING, EXPIRED",
            "DISPATCHING, CANCELLED",
            "RESERVED, AVAILABLE",
            "RESERVED, DISPATCHING",
            "RESERVED, PICKED_UP",
            "RESERVED, EXPIRED",
            "RESERVED, CANCELLED",
            "PICKED_UP, DELIVERED",
            "PICKED_UP, CANCELLED"
    })
    void legalTransitions_UpdateStatusAndWriteHistory(DonationStatus from, DonationStatus to) {
        Donation donation = createDonation(1L, from);
        when(donationRepository.save(any(Donation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Donation result = donationService.changeStatus(donation, to, donorUser, "Testing legal transition");

        assertEquals(to, result.getStatus());
        verify(donationRepository).save(donation);

        ArgumentCaptor<DonationStatusHistory> historyCaptor = ArgumentCaptor.forClass(DonationStatusHistory.class);
        verify(historyRepository).save(historyCaptor.capture());

        DonationStatusHistory history = historyCaptor.getValue();
        assertEquals(donation, history.getDonation());
        assertEquals(from, history.getFromStatus());
        assertEquals(to, history.getToStatus());
        assertEquals(donorUser, history.getChangedBy());
        assertEquals("Testing legal transition", history.getNote());
    }

    // -------------------------------------------------------------
    // Illegal Transitions
    // -------------------------------------------------------------

    @ParameterizedTest
    @CsvSource({
            "DELIVERED, AVAILABLE",
            "DELIVERED, CANCELLED",
            "DELIVERED, RESERVED",
            "EXPIRED, AVAILABLE",
            "EXPIRED, RESERVED",
            "CANCELLED, AVAILABLE",
            "CANCELLED, RESERVED",
            "AVAILABLE, DELIVERED",
            "AVAILABLE, PICKED_UP",
            "RESERVED, DELIVERED",
            "PICKED_UP, AVAILABLE",
            "PICKED_UP, RESERVED",
            "AVAILABLE, AVAILABLE",
            "RESERVED, RESERVED",
            "PICKED_UP, PICKED_UP",
            "DELIVERED, DELIVERED"
    })
    void illegalTransitions_ThrowInvalidStatusTransitionException(DonationStatus from, DonationStatus to) {
        Donation donation = createDonation(1L, from);

        InvalidStatusTransitionException ex = assertThrows(
                InvalidStatusTransitionException.class,
                () -> donationService.changeStatus(donation, to, donorUser, "Illegal attempt")
        );

        assertTrue(ex.getMessage().contains("Illegal status transition from " + from.name() + " to " + to.name()));
        verify(donationRepository, never()).save(any());
        verify(historyRepository, never()).save(any());
        verify(claimRepository, never()).save(any());
    }

    // -------------------------------------------------------------
    // Claim Closing Behavior on EXPIRED and CANCELLED
    // -------------------------------------------------------------

    @Test
    void changeStatus_ToExpired_ClosesActiveClaimAndClearsActiveDonationId() {
        Donation donation = createDonation(7L, DonationStatus.RESERVED);
        Claim claim = new Claim(donation, ngoUser);
        claim.setId(101L);
        assertEquals(7L, claim.getActiveDonationId());
        assertEquals(ClaimStatus.ACTIVE, claim.getStatus());

        when(donationRepository.save(any(Donation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(claimRepository.findByActiveDonationId(7L)).thenReturn(Optional.of(claim));

        Donation result = donationService.changeStatus(donation, DonationStatus.EXPIRED, null, "Safe window elapsed");

        assertEquals(DonationStatus.EXPIRED, result.getStatus());
        assertEquals(ClaimStatus.EXPIRED, claim.getStatus());
        assertNull(claim.getActiveDonationId(), "Unique active_donation_id must be cleared to null upon expiry");

        verify(claimRepository).save(claim);
        verify(historyRepository).save(any(DonationStatusHistory.class));
    }

    @Test
    void changeStatus_ToCancelled_ClosesActiveClaimWithReasonAndTimestamp() {
        Donation donation = createDonation(8L, DonationStatus.RESERVED);
        Claim claim = new Claim(donation, ngoUser);
        claim.setId(102L);

        when(donationRepository.save(any(Donation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(claimRepository.findByActiveDonationId(8L)).thenReturn(Optional.of(claim));

        Donation result = donationService.changeStatus(donation, DonationStatus.CANCELLED, donorUser, "Spilled food");

        assertEquals(DonationStatus.CANCELLED, result.getStatus());
        assertEquals(ClaimStatus.CANCELLED, claim.getStatus());
        assertEquals("Spilled food", claim.getCancelReason());
        assertNotNull(claim.getCancelledAt());
        assertNull(claim.getActiveDonationId(), "Unique active_donation_id must be cleared to null upon cancellation");

        verify(claimRepository).save(claim);
    }

    @Test
    void changeStatus_ToExpired_WhenNoClaimExists_CompletesWithoutError() {
        Donation donation = createDonation(9L, DonationStatus.AVAILABLE);
        when(donationRepository.save(any(Donation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(claimRepository.findByActiveDonationId(9L)).thenReturn(Optional.empty());

        Donation result = donationService.changeStatus(donation, DonationStatus.EXPIRED, null, "Unclaimed expiry");

        assertEquals(DonationStatus.EXPIRED, result.getStatus());
        verify(donationRepository).save(donation);
        verify(claimRepository, never()).save(any());
    }

    @Test
    void changeStatus_ToPickedUp_DoesNotCloseClaim() {
        Donation donation = createDonation(10L, DonationStatus.RESERVED);
        when(donationRepository.save(any(Donation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        donationService.changeStatus(donation, DonationStatus.PICKED_UP, ngoUser, "Food picked up by NGO");

        verify(claimRepository, never()).findByActiveDonationId(any());
        verify(claimRepository, never()).save(any());
    }

    // -------------------------------------------------------------
    // Helper Methods & Null Validations
    // -------------------------------------------------------------

    @Test
    void isTransitionAllowed_ReturnsCorrectBooleans() {
        assertTrue(donationService.isTransitionAllowed(DonationStatus.AVAILABLE, DonationStatus.RESERVED));
        assertTrue(donationService.isTransitionAllowed(DonationStatus.RESERVED, DonationStatus.PICKED_UP));
        assertTrue(donationService.isTransitionAllowed(DonationStatus.PICKED_UP, DonationStatus.DELIVERED));

        assertFalse(donationService.isTransitionAllowed(DonationStatus.DELIVERED, DonationStatus.AVAILABLE));
        assertFalse(donationService.isTransitionAllowed(null, DonationStatus.AVAILABLE));
        assertFalse(donationService.isTransitionAllowed(DonationStatus.AVAILABLE, null));
    }

    @Test
    void getAllowedTransitions_ReturnsUnmodifiableSet() {
        var transitions = donationService.getAllowedTransitions(DonationStatus.PICKED_UP);
        assertEquals(2, transitions.size());
        assertTrue(transitions.contains(DonationStatus.DELIVERED));
        assertTrue(transitions.contains(DonationStatus.CANCELLED));

        assertTrue(donationService.getAllowedTransitions(DonationStatus.DELIVERED).isEmpty());
        assertTrue(donationService.getAllowedTransitions(null).isEmpty());
    }

    @Test
    void nullArguments_ThrowExceptions() {
        assertThrows(NullPointerException.class, () ->
                donationService.changeStatus(null, DonationStatus.RESERVED, donorUser, "Note")
        );

        Donation donation = createDonation(1L, DonationStatus.AVAILABLE);
        assertThrows(NullPointerException.class, () ->
                donationService.changeStatus(donation, null, donorUser, "Note")
        );
    }

    @Test
    void createDonation_Success_WithExplicitCoordinates() {
        CreateDonationRequest request = new CreateDonationRequest(
                "Biryani",
                FoodCategory.COOKED_MEAL,
                50,
                "Hot biryani",
                "Club Road",
                15.84,
                74.49,
                Instant.now(),
                Instant.now().plus(Duration.ofHours(4))
        );

        when(userRepository.findByEmail("donor@example.com")).thenReturn(Optional.of(donorUser));
        when(donationRepository.save(any(Donation.class))).thenAnswer(inv -> {
            Donation d = inv.getArgument(0);
            d.setId(10L);
            return d;
        });

        DonationResponse response = donationService.createDonation(request, "donor@example.com");

        assertNotNull(response);
        assertEquals(10L, response.id());
        assertEquals("Biryani", response.foodName());
        assertEquals(DonationStatus.AVAILABLE, response.status());
        assertEquals(15.84, response.latitude());
        assertEquals(74.49, response.longitude());

        verify(historyRepository).save(any(DonationStatusHistory.class));
    }

    @Test
    void createDonation_FallsBackToDonorCoordinates_WhenOmitted() {
        donorUser.setLatitude(15.84);
        donorUser.setLongitude(74.49);

        CreateDonationRequest request = new CreateDonationRequest(
                "Biryani",
                FoodCategory.COOKED_MEAL,
                50,
                null,
                "Club Road",
                null,
                null,
                Instant.now(),
                Instant.now().plus(Duration.ofHours(4))
        );

        when(userRepository.findByEmail("donor@example.com")).thenReturn(Optional.of(donorUser));
        when(donationRepository.save(any(Donation.class))).thenAnswer(inv -> {
            Donation d = inv.getArgument(0);
            d.setId(11L);
            return d;
        });

        DonationResponse response = donationService.createDonation(request, "donor@example.com");

        assertNotNull(response);
        assertEquals(15.84, response.latitude());
        assertEquals(74.49, response.longitude());
    }

    @Test
    void createDonation_Fails_WhenNoCoordinatesAndNoDonorSavedLocation() {
        donorUser.setLatitude(null);
        donorUser.setLongitude(null);

        CreateDonationRequest request = new CreateDonationRequest(
                "Biryani",
                FoodCategory.COOKED_MEAL,
                50,
                null,
                "Club Road",
                null,
                null,
                Instant.now(),
                Instant.now().plus(Duration.ofHours(4))
        );

        when(userRepository.findByEmail("donor@example.com")).thenReturn(Optional.of(donorUser));

        ValidationException ex = assertThrows(ValidationException.class, () ->
                donationService.createDonation(request, "donor@example.com")
        );
        assertTrue(ex.getFieldErrors().containsKey("latitude"));
        assertTrue(ex.getFieldErrors().containsKey("longitude"));
    }

    @Test
    void createDonation_Fails_WhenSafeUntilLessThan30Minutes() {
        CreateDonationRequest request = new CreateDonationRequest(
                "Biryani",
                FoodCategory.COOKED_MEAL,
                50,
                null,
                "Club Road",
                15.84,
                74.49,
                Instant.now(),
                Instant.now().plus(Duration.ofMinutes(10)) // less than 30 min
        );

        when(userRepository.findByEmail("donor@example.com")).thenReturn(Optional.of(donorUser));

        ValidationException ex = assertThrows(ValidationException.class, () ->
                donationService.createDonation(request, "donor@example.com")
        );
        assertTrue(ex.getFieldErrors().containsKey("safeUntil"));
    }

    @Test
    void getMyDonations_ReturnsDonationsForDonor() {
        donorUser.setId(1L);
        Donation d1 = createDonation(10L, DonationStatus.AVAILABLE);
        d1.setDonor(donorUser);

        when(userRepository.findByEmail("donor@example.com")).thenReturn(Optional.of(donorUser));
        when(donationRepository.findByDonorIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(d1));

        List<DonationResponse> list = donationService.getMyDonations("donor@example.com", null);

        assertEquals(1, list.size());
        assertEquals(10L, list.get(0).id());
    }

    @Test
    void getDonationById_PreventsDonorFromViewingOthersDonation() {
        donorUser.setId(1L);

        User otherDonor = new User("Other", "other@example.com", "hash", Role.DONOR);
        otherDonor.setId(2L);

        Donation donation = createDonation(10L, DonationStatus.AVAILABLE);
        donation.setDonor(otherDonor);

        when(donationRepository.findById(10L)).thenReturn(Optional.of(donation));

        assertThrows(AccessDeniedException.class, () ->
                donationService.getDonationById(10L, donorUser)
        );
    }

    @Test
    void getDonationById_AllowsNGOToViewDonation() {
        donorUser.setId(1L);
        ngoUser.setId(2L);

        Donation donation = createDonation(10L, DonationStatus.AVAILABLE);
        donation.setDonor(donorUser);

        when(donationRepository.findById(10L)).thenReturn(Optional.of(donation));
        when(claimRepository.findByActiveDonationId(10L)).thenReturn(Optional.empty());

        DonationResponse response = donationService.getDonationById(10L, ngoUser);

        assertNotNull(response);
        assertEquals(10L, response.id());
    }

    @Test
    void getDonationHistory_ShowsSystemWhenChangedByNull() {
        donorUser.setId(1L);
        Donation donation = createDonation(10L, DonationStatus.EXPIRED);
        donation.setDonor(donorUser);

        DonationStatusHistory h1 = new DonationStatusHistory(donation, null, DonationStatus.AVAILABLE, donorUser, "Donation posted");
        DonationStatusHistory h2 = new DonationStatusHistory(donation, DonationStatus.AVAILABLE, DonationStatus.EXPIRED, null, "Safe until passed");

        when(donationRepository.findById(10L)).thenReturn(Optional.of(donation));
        when(historyRepository.findByDonationIdOrderByChangedAtAsc(10L)).thenReturn(List.of(h1, h2));

        List<DonationHistoryResponse> history = donationService.getDonationHistory(10L, donorUser);

        assertEquals(2, history.size());
        assertEquals("Donor Hotel", history.get(0).changedBy());
        assertEquals("System", history.get(1).changedBy());
    }

    // --- Task S1: expireOverdueDonations tests ---

    @Test
    void expireOverdueDonations_expiresPastDueAvailableAndReservedDonations() {
        Instant now = Instant.parse("2026-11-20T12:00:00Z");

        Donation d1 = createDonation(1L, DonationStatus.AVAILABLE);
        d1.setSafeUntil(now.minusSeconds(60));

        Donation d2 = createDonation(2L, DonationStatus.RESERVED);
        d2.setSafeUntil(now.minusSeconds(120));

        when(donationRepository.findByStatusInAndSafeUntilBefore(any(), eq(now)))
                .thenReturn(List.of(d1, d2));
        when(donationRepository.save(any(Donation.class))).thenAnswer(i -> i.getArgument(0));
        when(claimRepository.findByActiveDonationId(1L)).thenReturn(Optional.empty());
        when(claimRepository.findByActiveDonationId(2L)).thenReturn(Optional.empty());

        int count = donationService.expireOverdueDonations(now);

        assertEquals(2, count);
        assertEquals(DonationStatus.EXPIRED, d1.getStatus());
        assertEquals(DonationStatus.EXPIRED, d2.getStatus());

        ArgumentCaptor<DonationStatusHistory> historyCaptor = ArgumentCaptor.forClass(DonationStatusHistory.class);
        verify(historyRepository, times(2)).save(historyCaptor.capture());

        List<DonationStatusHistory> histories = historyCaptor.getAllValues();
        assertEquals(DonationStatus.AVAILABLE, histories.get(0).getFromStatus());
        assertEquals(DonationStatus.EXPIRED, histories.get(0).getToStatus());
        assertNull(histories.get(0).getChangedBy()); // null represents System
        assertEquals("Food expired past safeUntil", histories.get(0).getNote());

        assertEquals(DonationStatus.RESERVED, histories.get(1).getFromStatus());
        assertEquals(DonationStatus.EXPIRED, histories.get(1).getToStatus());
        assertNull(histories.get(1).getChangedBy());
    }

    @Test
    void expireOverdueDonations_closesActiveClaimOnReservedDonation() {
        Instant now = Instant.parse("2026-11-20T12:00:00Z");

        Donation d1 = createDonation(1L, DonationStatus.RESERVED);
        d1.setSafeUntil(now.minusSeconds(60));

        Claim claim = new Claim(d1, ngoUser);
        claim.setId(10L);
        claim.setStatus(ClaimStatus.ACTIVE);
        claim.setActiveDonationId(1L);

        when(donationRepository.findByStatusInAndSafeUntilBefore(any(), eq(now)))
                .thenReturn(List.of(d1));
        when(donationRepository.save(any(Donation.class))).thenAnswer(i -> i.getArgument(0));
        when(claimRepository.findByActiveDonationId(1L)).thenReturn(Optional.of(claim));

        int count = donationService.expireOverdueDonations(now);

        assertEquals(1, count);
        assertEquals(DonationStatus.EXPIRED, d1.getStatus());
        assertEquals(ClaimStatus.EXPIRED, claim.getStatus());
        assertNull(claim.getActiveDonationId()); // unique constraint released

        verify(claimRepository).save(claim);
    }

    @Test
    void expireOverdueDonations_idempotent_runningTwiceChangesNothing() {
        Instant now = Instant.parse("2026-11-20T12:00:00Z");

        Donation d1 = createDonation(1L, DonationStatus.AVAILABLE);
        d1.setSafeUntil(now.minusSeconds(60));

        // First run returns d1
        when(donationRepository.findByStatusInAndSafeUntilBefore(any(), eq(now)))
                .thenReturn(List.of(d1))
                .thenReturn(List.of()); // Second run returns empty list

        when(donationRepository.save(any(Donation.class))).thenAnswer(i -> i.getArgument(0));
        when(claimRepository.findByActiveDonationId(1L)).thenReturn(Optional.empty());

        int count1 = donationService.expireOverdueDonations(now);
        assertEquals(1, count1);
        assertEquals(DonationStatus.EXPIRED, d1.getStatus());

        // Second run: no overdue donations found because d1 is now EXPIRED
        int count2 = donationService.expireOverdueDonations(now);
        assertEquals(0, count2);
    }
}
