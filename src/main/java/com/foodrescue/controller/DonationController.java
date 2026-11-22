package com.foodrescue.controller;

import java.security.Principal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.foodrescue.dto.ClaimResponse;
import com.foodrescue.dto.CreateDonationRequest;
import com.foodrescue.dto.DonationFeedResponse;
import com.foodrescue.dto.DonationHistoryResponse;
import com.foodrescue.dto.DonationResponse;
import com.foodrescue.entity.User;
import com.foodrescue.enums.DonationStatus;
import com.foodrescue.enums.FoodCategory;
import com.foodrescue.exception.ResourceNotFoundException;
import com.foodrescue.repository.UserRepository;
import com.foodrescue.service.ClaimService;
import com.foodrescue.service.DonationService;
import com.foodrescue.service.FeedService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/donations")
public class DonationController {

    private final DonationService donationService;
    private final FeedService feedService;
    private final ClaimService claimService;
    private final UserRepository userRepository;

    public DonationController(DonationService donationService,
                              FeedService feedService,
                              ClaimService claimService,
                              UserRepository userRepository) {
        this.donationService = donationService;
        this.feedService = feedService;
        this.claimService = claimService;
        this.userRepository = userRepository;
    }

    @GetMapping("/feed")
    public ResponseEntity<List<DonationFeedResponse>> getFeed(
            @RequestParam(required = false) FoodCategory category,
            @RequestParam(required = false) Integer minQuantity,
            @RequestParam(required = false) Double maxDistanceKm,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw new BadCredentialsException("Not authenticated");
        }
        List<DonationFeedResponse> feed = feedService.getFeed(
                principal.getName(),
                category,
                minQuantity,
                maxDistanceKm,
                page,
                size
        );
        return ResponseEntity.ok(feed);
    }

    @PostMapping
    public ResponseEntity<DonationResponse> createDonation(@Valid @RequestBody CreateDonationRequest request,
                                                           Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw new BadCredentialsException("Not authenticated");
        }
        DonationResponse response = donationService.createDonation(request, principal.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/mine")
    public ResponseEntity<List<DonationResponse>> getMyDonations(@RequestParam(required = false) DonationStatus status,
                                                                 Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw new BadCredentialsException("Not authenticated");
        }
        List<DonationResponse> list = donationService.getMyDonations(principal.getName(), status);
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DonationResponse> getDonationById(@PathVariable Long id, Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw new BadCredentialsException("Not authenticated");
        }
        User currentUser = userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + principal.getName()));
        DonationResponse response = donationService.getDonationById(id, currentUser);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/claim")
    public ResponseEntity<ClaimResponse> claimDonation(@PathVariable Long id, Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw new BadCredentialsException("Not authenticated");
        }
        ClaimResponse response = claimService.claim(id, principal.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<List<DonationHistoryResponse>> getDonationHistory(@PathVariable Long id, Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw new BadCredentialsException("Not authenticated");
        }
        User currentUser = userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + principal.getName()));
        List<DonationHistoryResponse> history = donationService.getDonationHistory(id, currentUser);
        return ResponseEntity.ok(history);
    }
}
