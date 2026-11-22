package com.foodrescue.service;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.foodrescue.algorithm.DistanceUtil;
import com.foodrescue.dto.ClaimResponse;
import com.foodrescue.entity.Claim;
import com.foodrescue.entity.Donation;
import com.foodrescue.entity.NgoProfile;
import com.foodrescue.entity.User;
import com.foodrescue.enums.DonationStatus;
import com.foodrescue.exception.DonationNotClaimableException;
import com.foodrescue.exception.NgoNotEligibleException;
import com.foodrescue.exception.ResourceNotFoundException;
import com.foodrescue.repository.ClaimRepository;
import com.foodrescue.repository.DonationRepository;
import com.foodrescue.repository.NgoProfileRepository;
import com.foodrescue.repository.UserRepository;

/**
 * Service managing food rescue claims and atomic reservation.
 * <p>
 * Core showpiece per ARCHITECTURE.md §5 and BACKEND_ROADMAP.md Step 9.
 * Enforces the strict check order: 404 (Not Found) -> 409 (Not Claimable) -> 400 (Not Eligible).
 * </p>
 */
@Service
public class ClaimService {

    private final DonationRepository donationRepository;
    private final ClaimRepository claimRepository;
    private final UserRepository userRepository;
    private final NgoProfileRepository ngoProfileRepository;
    private final DonationService donationService;

    public ClaimService(DonationRepository donationRepository,
                        ClaimRepository claimRepository,
                        UserRepository userRepository,
                        NgoProfileRepository ngoProfileRepository,
                        DonationService donationService) {
        this.donationRepository = donationRepository;
        this.claimRepository = claimRepository;
        this.userRepository = userRepository;
        this.ngoProfileRepository = ngoProfileRepository;
        this.donationService = donationService;
    }

    /**
     * Claims an available donation for an NGO in a single transaction.
     *
     * @param donationId the ID of the donation to claim
     * @param ngoEmail   the email of the claiming NGO user
     * @return the created {@link ClaimResponse}
     * @throws ResourceNotFoundException     if donation or user is not found (404)
     * @throws DonationNotClaimableException if donation cannot be claimed (409)
     * @throws NgoNotEligibleException       if NGO fails eligibility rules (400)
     */
    @Transactional
    public ClaimResponse claim(Long donationId, String ngoEmail) {
        return claim(donationId, ngoEmail, Instant.now());
    }

    /**
     * Overloaded method allowing an explicit timestamp (e.g. for testing).
     */
    @Transactional
    public ClaimResponse claim(Long donationId, String ngoEmail, Instant now) {
        Objects.requireNonNull(donationId, "Donation ID must not be null");
        Objects.requireNonNull(ngoEmail, "NGO email must not be null");
        if (now == null) {
            now = Instant.now();
        }

        // 1. Check 404: Load donation
        Donation donation = donationRepository.findById(donationId)
                .orElseThrow(() -> new ResourceNotFoundException("Donation not found with id: " + donationId));

        // 2. Check 409: Donation claimable status, expiry, and active claim check
        DonationStatus currentStatus = donation.getStatus();
        boolean isClaimableStatus = currentStatus == DonationStatus.AVAILABLE || currentStatus == DonationStatus.DISPATCHING;
        boolean isPastSafeUntil = donation.getSafeUntil() != null && !donation.getSafeUntil().isAfter(now);
        boolean hasActiveClaim = claimRepository.findByActiveDonationId(donationId).isPresent();

        if (!isClaimableStatus || isPastSafeUntil || hasActiveClaim) {
            throw new DonationNotClaimableException("Donation is no longer available.");
        }

        // 3. Check 400: NGO Eligibility rules per ALGORITHMS.md §3.1
        User ngoUser = userRepository.findByEmail(ngoEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + ngoEmail));

        // Rule 1: NGO user is active
        if (!ngoUser.isActive()) {
            throw new NgoNotEligibleException("NGO account is inactive");
        }

        NgoProfile ngoProfile = ngoProfileRepository.findByUserId(ngoUser.getId())
                .orElseThrow(() -> new NgoNotEligibleException("NGO profile not configured for user: " + ngoEmail));

        // Rule 2: Donation category is accepted by the NGO
        if (!ngoProfile.acceptsCategory(donation.getCategory())) {
            throw new NgoNotEligibleException("NGO does not accept food category: " + donation.getCategory());
        }

        // Rule 3: Pickup location within NGO service radius
        Double ngoLat = ngoUser.getLatitude();
        Double ngoLng = ngoUser.getLongitude();
        Double donationLat = donation.getLatitude();
        Double donationLng = donation.getLongitude();

        if (ngoLat == null || ngoLng == null || donationLat == null || donationLng == null) {
            throw new NgoNotEligibleException("Coordinates unavailable to determine distance");
        }

        double distanceKm = DistanceUtil.calculateDistanceKm(ngoLat, ngoLng, donationLat, donationLng);
        if (distanceKm > ngoProfile.getServiceRadiusKm()) {
            throw new NgoNotEligibleException(String.format(
                    Locale.ROOT,
                    "Donation pickup location (%.1f km) exceeds NGO service radius (%.1f km)",
                    distanceKm,
                    ngoProfile.getServiceRadiusKm()
            ));
        }

        // Note: Exceeding nominal capacity does NOT reject the claim (NGO may claim the full quantity)

        // 4. Create active Claim
        Claim claim = new Claim(donation, ngoUser);
        claim.setClaimedAt(now);

        // 5. Change donation status to RESERVED (bumps @Version on donation)
        donationService.changeStatus(donation, DonationStatus.RESERVED, ngoUser, "Claimed by " + ngoUser.getName());

        // 6. Save and flush to guarantee concurrency conflict detection (@Version or unique active_donation_id)
        // occurs inside the transaction boundary
        Claim savedClaim = claimRepository.saveAndFlush(claim);
        donationRepository.saveAndFlush(donation);

        return ClaimResponse.from(savedClaim);
    }
}
