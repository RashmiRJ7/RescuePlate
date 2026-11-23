package com.foodrescue.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.foodrescue.dto.ClaimSummaryResponse;
import com.foodrescue.dto.CreateDonationRequest;
import com.foodrescue.dto.DonationHistoryResponse;
import com.foodrescue.dto.DonationResponse;
import com.foodrescue.entity.Donation;
import com.foodrescue.entity.DonationStatusHistory;
import com.foodrescue.entity.User;
import com.foodrescue.enums.DonationStatus;
import com.foodrescue.enums.Role;
import com.foodrescue.exception.InvalidStatusTransitionException;
import com.foodrescue.exception.ResourceNotFoundException;
import com.foodrescue.exception.ValidationException;
import com.foodrescue.repository.ClaimRepository;
import com.foodrescue.repository.DonationRepository;
import com.foodrescue.repository.DonationStatusHistoryRepository;
import com.foodrescue.repository.UserRepository;

/**
 * Core business service managing the Donation lifecycle.
 * <p>
 * Implements the centralized state machine per ARCHITECTURE.md §4.
 * All status changes MUST pass through {@link #changeStatus(Donation, DonationStatus, User, String)}.
 * Nothing else in the codebase is permitted to call {@code donation.setStatus(...)}.
 * </p>
 */
@Service
public class DonationService {

    /**
     * Centralized transition table defining all legal status moves.
     * Any transition not present in this map throws {@link InvalidStatusTransitionException}.
     */
    private static final Map<DonationStatus, Set<DonationStatus>> ALLOWED_TRANSITIONS = Map.of(
            DonationStatus.AVAILABLE, Set.of(
                    DonationStatus.DISPATCHING,
                    DonationStatus.RESERVED,
                    DonationStatus.EXPIRED,
                    DonationStatus.CANCELLED
            ),
            DonationStatus.DISPATCHING, Set.of(
                    DonationStatus.AVAILABLE,
                    DonationStatus.RESERVED,
                    DonationStatus.EXPIRED,
                    DonationStatus.CANCELLED
            ),
            DonationStatus.RESERVED, Set.of(
                    DonationStatus.AVAILABLE,
                    DonationStatus.DISPATCHING,
                    DonationStatus.PICKED_UP,
                    DonationStatus.EXPIRED,
                    DonationStatus.CANCELLED
            ),
            DonationStatus.PICKED_UP, Set.of(
                    DonationStatus.DELIVERED,
                    DonationStatus.CANCELLED
            ),
            DonationStatus.DELIVERED, Set.of(),
            DonationStatus.EXPIRED, Set.of(),
            DonationStatus.CANCELLED, Set.of()
    );

    private final DonationRepository donationRepository;
    private final DonationStatusHistoryRepository historyRepository;
    private final ClaimRepository claimRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public DonationService(DonationRepository donationRepository,
                           DonationStatusHistoryRepository historyRepository,
                           ClaimRepository claimRepository,
                           UserRepository userRepository,
                           NotificationService notificationService) {
        this.donationRepository = donationRepository;
        this.historyRepository = historyRepository;
        this.claimRepository = claimRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    /**
     * Checks if a transition between two statuses is permitted by the state machine.
     *
     * @param from current status
     * @param to   target status
     * @return true if legal, false otherwise
     */
    public boolean isTransitionAllowed(DonationStatus from, DonationStatus to) {
        if (from == null || to == null) {
            return false;
        }
        return ALLOWED_TRANSITIONS.getOrDefault(from, Collections.emptySet()).contains(to);
    }

    /**
     * Returns an unmodifiable set of all legal destination statuses from the given status.
     *
     * @param from current status
     * @return set of allowed target statuses
     */
    public Set<DonationStatus> getAllowedTransitions(DonationStatus from) {
        if (from == null) {
            return Collections.emptySet();
        }
        return ALLOWED_TRANSITIONS.getOrDefault(from, Collections.emptySet());
    }

    /**
     * Centralized state transition method.
     * <ol>
     *   <li>Validates that {@code currentStatus -> newStatus} is legal.</li>
     *   <li>Updates {@code donation.status} to {@code newStatus}.</li>
     *   <li>Persists an immutable {@link DonationStatusHistory} audit log.</li>
     *   <li>Closes any active claim if moving to {@code EXPIRED} or {@code CANCELLED}.</li>
     * </ol>
     *
     * @param donation  the donation to update (must not be null)
     * @param newStatus the target status (must not be null)
     * @param actor     the user triggering the change, or null for system actions (e.g. ExpiryMonitor)
     * @param note      optional context note describing the reason for the transition
     * @return the saved, updated {@link Donation} entity
     * @throws InvalidStatusTransitionException if the transition is illegal
     */
    @Transactional
    public Donation changeStatus(Donation donation, DonationStatus newStatus, User actor, String note) {
        Objects.requireNonNull(donation, "Donation must not be null");
        Objects.requireNonNull(newStatus, "Target status must not be null");

        DonationStatus currentStatus = donation.getStatus();
        if (currentStatus == null || !isTransitionAllowed(currentStatus, newStatus)) {
            String fromStr = currentStatus != null ? currentStatus.name() : "null";
            throw new InvalidStatusTransitionException(fromStr, newStatus.name());
        }

        // 1. Update status and save donation
        donation.setStatus(newStatus);
        Donation savedDonation = donationRepository.save(donation);

        // 2. Record immutable status history audit log
        DonationStatusHistory history = new DonationStatusHistory(
                savedDonation,
                currentStatus,
                newStatus,
                actor,
                note
        );
        historyRepository.save(history);

        // 3. Automatically close any active claim if moving to terminal EXPIRED or CANCELLED
        if (newStatus == DonationStatus.EXPIRED || newStatus == DonationStatus.CANCELLED) {
            if (savedDonation.getId() != null) {
                claimRepository.findByActiveDonationId(savedDonation.getId()).ifPresent(claim -> {
                    if (newStatus == DonationStatus.EXPIRED) {
                        claim.markExpired();
                    } else {
                        claim.markCancelled(note != null ? note : "Donation cancelled", Instant.now());
                    }
                    claimRepository.save(claim);
                });
            }
        }

        return savedDonation;
    }

    @Transactional
    public DonationResponse createDonation(CreateDonationRequest request, String donorEmail) {
        Objects.requireNonNull(request, "Request must not be null");

        User donor = userRepository.findByEmail(donorEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Donor not found with email: " + donorEmail));

        Double lat = request.latitude();
        Double lng = request.longitude();
        Map<String, String> fieldErrors = new HashMap<>();

        if ((lat == null && lng != null) || (lat != null && lng == null)) {
            fieldErrors.put("latitude", "Both latitude and longitude must be provided together");
            fieldErrors.put("longitude", "Both latitude and longitude must be provided together");
        } else if (lat == null && lng == null) {
            if (donor.getLatitude() != null && donor.getLongitude() != null) {
                lat = donor.getLatitude();
                lng = donor.getLongitude();
            } else {
                fieldErrors.put("latitude", "Latitude is required when donor has no saved location");
                fieldErrors.put("longitude", "Longitude is required when donor has no saved location");
            }
        } else {
            if (lat < -90.0 || lat > 90.0) {
                fieldErrors.put("latitude", "must be between -90 and 90");
            }
            if (lng < -180.0 || lng > 180.0) {
                fieldErrors.put("longitude", "must be between -180 and 180");
            }
        }

        Instant now = Instant.now();
        Instant readyFrom = request.readyFrom() != null ? request.readyFrom() : now;
        if (request.safeUntil() != null) {
            if (request.safeUntil().isBefore(readyFrom)) {
                fieldErrors.put("safeUntil", "must be after readyFrom");
            } else if (request.safeUntil().isBefore(now.plus(Duration.ofMinutes(30)))) {
                fieldErrors.put("safeUntil", "must be at least 30 minutes from now");
            }
        }

        if (!fieldErrors.isEmpty()) {
            throw new ValidationException(fieldErrors);
        }

        Donation donation = new Donation(
                donor,
                request.foodName(),
                request.category(),
                request.quantity(),
                request.pickupAddress(),
                lat,
                lng,
                readyFrom,
                request.safeUntil()
        );
        donation.setDescription(request.description());
        donation.setStatus(DonationStatus.AVAILABLE);

        Donation saved = donationRepository.save(donation);

        DonationStatusHistory initialHistory = new DonationStatusHistory(
                saved,
                null,
                DonationStatus.AVAILABLE,
                donor,
                "Donation posted"
        );
        historyRepository.save(initialHistory);

        notificationService.notifyNewDonation(saved);

        return DonationResponse.from(saved, now, null);
    }

    @Transactional(readOnly = true)
    public List<DonationResponse> getMyDonations(String donorEmail, DonationStatus status) {
        User donor = userRepository.findByEmail(donorEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Donor not found with email: " + donorEmail));

        List<Donation> list = status != null
                ? donationRepository.findByDonorIdAndStatusOrderByCreatedAtDesc(donor.getId(), status)
                : donationRepository.findByDonorIdOrderByCreatedAtDesc(donor.getId());

        Instant now = Instant.now();
        return list.stream().map(d -> {
            ClaimSummaryResponse claimSummary = null;
            if (d.getStatus() == DonationStatus.RESERVED || d.getStatus() == DonationStatus.PICKED_UP) {
                claimSummary = claimRepository.findByActiveDonationId(d.getId())
                        .map(c -> new ClaimSummaryResponse(c.getId(), c.getNgo().getName(), c.getClaimedAt()))
                        .orElse(null);
            }
            return DonationResponse.from(d, now, claimSummary);
        }).toList();
    }

    @Transactional(readOnly = true)
    public DonationResponse getDonationById(Long id, User currentUser) {
        Donation donation = donationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Donation not found with id: " + id));

        if (currentUser.getRole() == Role.DONOR && !donation.getDonor().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("Access denied.");
        }

        ClaimSummaryResponse claimSummary = claimRepository.findByActiveDonationId(donation.getId())
                .map(c -> new ClaimSummaryResponse(c.getId(), c.getNgo().getName(), c.getClaimedAt()))
                .orElse(null);

        return DonationResponse.from(donation, Instant.now(), claimSummary);
    }

    @Transactional(readOnly = true)
    public List<DonationHistoryResponse> getDonationHistory(Long id, User currentUser) {
        Donation donation = donationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Donation not found with id: " + id));

        if (currentUser.getRole() == Role.DONOR && !donation.getDonor().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("Access denied.");
        }

        return historyRepository.findByDonationIdOrderByChangedAtAsc(id).stream()
                .map(DonationHistoryResponse::from)
                .toList();
    }

    /**
     * Finds and transitions all overdue claimable/reserved donations to EXPIRED (Task S1).
     * <p>
     * Searches for donations with status AVAILABLE, DISPATCHING, or RESERVED where safeUntil &lt; now.
     * Transitions each to EXPIRED with actor = null (representing System), which automatically closes
     * any active claim and records audit history.
     * </p>
     *
     * @return count of newly expired donations
     */
    @Transactional
    public int expireOverdueDonations() {
        return expireOverdueDonations(Instant.now());
    }

    /**
     * Overloaded method with explicit timestamp for deterministic testing.
     *
     * @param now the timestamp to compare against safeUntil
     * @return count of newly expired donations
     */
    @Transactional
    public int expireOverdueDonations(Instant now) {
        Objects.requireNonNull(now, "Timestamp must not be null");

        List<DonationStatus> activeStatuses = List.of(
                DonationStatus.AVAILABLE,
                DonationStatus.DISPATCHING,
                DonationStatus.RESERVED
        );

        List<Donation> overdue = donationRepository.findByStatusInAndSafeUntilBefore(activeStatuses, now);
        int count = 0;
        for (Donation donation : overdue) {
            changeStatus(donation, DonationStatus.EXPIRED, null, "Food expired past safeUntil");
            count++;
        }
        return count;
    }
}
