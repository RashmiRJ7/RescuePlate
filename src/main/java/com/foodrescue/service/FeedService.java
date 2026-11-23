package com.foodrescue.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.foodrescue.algorithm.DistanceUtil;
import com.foodrescue.algorithm.ScoredDonation;
import com.foodrescue.algorithm.UrgencyScoreCalculator;
import com.foodrescue.config.ScoringProperties;
import com.foodrescue.dto.DonationFeedResponse;
import com.foodrescue.entity.Donation;
import com.foodrescue.entity.NgoProfile;
import com.foodrescue.entity.User;
import com.foodrescue.enums.DonationStatus;
import com.foodrescue.enums.FoodCategory;
import com.foodrescue.exception.ResourceNotFoundException;
import com.foodrescue.repository.DonationRepository;
import com.foodrescue.repository.NgoProfileRepository;
import com.foodrescue.repository.UserRepository;

@Service
public class FeedService {

    private final DonationRepository donationRepository;
    private final UserRepository userRepository;
    private final NgoProfileRepository ngoProfileRepository;
    private final UrgencyScoreCalculator urgencyCalculator;

    @Autowired
    public FeedService(DonationRepository donationRepository,
                       UserRepository userRepository,
                       NgoProfileRepository ngoProfileRepository,
                       ScoringProperties scoringProperties) {
        this.donationRepository = donationRepository;
        this.userRepository = userRepository;
        this.ngoProfileRepository = ngoProfileRepository;
        this.urgencyCalculator = new UrgencyScoreCalculator(scoringProperties.getUrgency());
    }

    public FeedService(DonationRepository donationRepository,
                       UserRepository userRepository,
                       NgoProfileRepository ngoProfileRepository,
                       UrgencyScoreCalculator urgencyCalculator) {
        this.donationRepository = donationRepository;
        this.userRepository = userRepository;
        this.ngoProfileRepository = ngoProfileRepository;
        this.urgencyCalculator = urgencyCalculator;
    }

    @Transactional(readOnly = true)
    public List<DonationFeedResponse> getFeed(String ngoEmail,
                                              FoodCategory categoryFilter,
                                              Integer minQuantityFilter,
                                              Double maxDistanceKmFilter,
                                              Integer page,
                                              Integer size) {
        return getFeed(ngoEmail, categoryFilter, minQuantityFilter, maxDistanceKmFilter, page, size, Instant.now());
    }

    @Transactional(readOnly = true)
    public List<DonationFeedResponse> getFeed(String ngoEmail,
                                              FoodCategory categoryFilter,
                                              Integer minQuantityFilter,
                                              Double maxDistanceKmFilter,
                                              Integer page,
                                              Integer size,
                                              Instant now) {
        User ngoUser = userRepository.findByEmail(ngoEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + ngoEmail));

        if (!ngoUser.isActive()) {
            return Collections.emptyList();
        }

        NgoProfile ngoProfile = ngoProfileRepository.findByUserId(ngoUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("NGO profile not found for user: " + ngoEmail));

        Double ngoLat = ngoUser.getLatitude();
        Double ngoLng = ngoUser.getLongitude();
        if (ngoLat == null || ngoLng == null) {
            throw new IllegalArgumentException(
                    "NGO location is not set. Add your location in NGO profile settings to see nearby donations."
            );
        }

        // 1. Candidate claimable donations from DB
        List<DonationStatus> claimableStatuses = List.of(DonationStatus.AVAILABLE, DonationStatus.DISPATCHING);
        List<Donation> candidates = donationRepository.findByStatusInAndSafeUntilAfter(claimableStatuses, now);

        double effectiveRadius = ngoProfile.getServiceRadiusKm();
        if (maxDistanceKmFilter != null) {
            effectiveRadius = Math.min(effectiveRadius, maxDistanceKmFilter);
        }

        // 2. Filter by NGO eligibility & query parameters
        List<ScoredDonation> eligibleScored = new ArrayList<>();

        for (Donation donation : candidates) {
            // Category eligibility
            if (!ngoProfile.acceptsCategory(donation.getCategory())) {
                continue;
            }
            if (categoryFilter != null && donation.getCategory() != categoryFilter) {
                continue;
            }

            // Min quantity filter
            if (minQuantityFilter != null && donation.getQuantity() < minQuantityFilter) {
                continue;
            }

            // Distance filter (capped at effective radius)
            double distanceKm = DistanceUtil.calculateDistanceKm(
                    ngoLat,
                    ngoLng,
                    donation.getLatitude(),
                    donation.getLongitude()
            );

            if (distanceKm > effectiveRadius) {
                continue;
            }

            // Calculate urgency score
            double urgencyScore = urgencyCalculator.calculate(donation, now);
            eligibleScored.add(new ScoredDonation(donation, urgencyScore));
        }

        // 3. Sort using 4-level tie breaking feed comparator
        eligibleScored.sort(ScoredDonation.comparator());

        // 4. Pagination
        int pageNum = page != null && page >= 0 ? page : 0;
        int pageSize = size != null && size > 0 ? size : 20;
        int fromIndex = pageNum * pageSize;
        if (fromIndex >= eligibleScored.size()) {
            return Collections.emptyList();
        }
        int toIndex = Math.min(fromIndex + pageSize, eligibleScored.size());
        List<ScoredDonation> paged = eligibleScored.subList(fromIndex, toIndex);

        // 5. Map to response DTOs
        return paged.stream().map(s -> {
            Donation d = s.donation();
            double dist = DistanceUtil.calculateDistanceKm(
                    ngoLat,
                    ngoLng,
                    d.getLatitude(),
                    d.getLongitude()
            );
            long minutesLeft = d.getMinutesLeft(now);
            boolean exceedsCapacity = ngoProfile.getCapacityMeals() != null
                    && d.getQuantity() > ngoProfile.getCapacityMeals();

            return new DonationFeedResponse(
                    d.getId(),
                    d.getFoodName(),
                    d.getCategory(),
                    d.getQuantity(),
                    d.getDonor().getName(),
                    d.getPickupAddress(),
                    dist,
                    minutesLeft,
                    d.getSafeUntil(),
                    s.urgencyScore(),
                    exceedsCapacity,
                    d.getStatus()
            );
        }).toList();
    }
}
