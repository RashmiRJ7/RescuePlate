package com.foodrescue.controller;

import java.security.Principal;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

import com.foodrescue.dto.AdminAccountRequest;
import com.foodrescue.dto.AdminClaimResponse;
import com.foodrescue.dto.AdminDonationResponse;
import com.foodrescue.dto.AdminStatsResponse;
import com.foodrescue.dto.AdminUserResponse;
import com.foodrescue.dto.AuthUserResponse;
import com.foodrescue.entity.Claim;
import com.foodrescue.entity.Donation;
import com.foodrescue.entity.User;
import com.foodrescue.enums.DonationStatus;
import com.foodrescue.enums.Role;
import com.foodrescue.exception.ResourceNotFoundException;
import com.foodrescue.repository.ClaimRepository;
import com.foodrescue.repository.DonationRepository;
import com.foodrescue.repository.UserRepository;
import com.foodrescue.service.AdminAccountService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserRepository userRepository;
    private final DonationRepository donationRepository;
    private final ClaimRepository claimRepository;
    private final AdminAccountService adminAccountService;

    public AdminController(UserRepository userRepository,
                           DonationRepository donationRepository,
                           ClaimRepository claimRepository,
                           AdminAccountService adminAccountService) {
        this.userRepository = userRepository;
        this.donationRepository = donationRepository;
        this.claimRepository = claimRepository;
        this.adminAccountService = adminAccountService;
    }

    @GetMapping("/stats")
    @Transactional(readOnly = true)
    public ResponseEntity<AdminStatsResponse> getStats(Principal principal) {
        requireAdmin(principal);
        List<User> users = userRepository.findAll();
        List<Donation> donations = donationRepository.findAll();

        long totalUsers = users.size();
        long totalDonors = users.stream().filter(u -> u.getRole() == Role.DONOR).count();
        long totalNgos = users.stream().filter(u -> u.getRole() == Role.NGO).count();
        long totalDonations = donations.size();
        long donationsAvailable = donations.stream()
                .filter(d -> d.getStatus() == DonationStatus.AVAILABLE).count();
        long donationsDelivered = donations.stream()
                .filter(d -> d.getStatus() == DonationStatus.DELIVERED).count();
        long donationsExpired = donations.stream()
                .filter(d -> d.getStatus() == DonationStatus.EXPIRED).count();

        return ResponseEntity.ok(new AdminStatsResponse(
                totalUsers, totalDonors, totalNgos, totalDonations,
                donationsAvailable, donationsDelivered, donationsExpired
        ));
    }

    @GetMapping("/users")
    @Transactional(readOnly = true)
    public ResponseEntity<List<AdminUserResponse>> getUsers(Principal principal) {
        requireAdmin(principal);
        List<AdminUserResponse> list = userRepository.findAll().stream()
                .map(this::toUserResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    @GetMapping("/donations")
    @Transactional(readOnly = true)
    public ResponseEntity<List<AdminDonationResponse>> getDonations(Principal principal) {
        requireAdmin(principal);
        List<AdminDonationResponse> list = donationRepository.findAll().stream()
                .map(this::toDonationResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    @GetMapping("/claims")
    @Transactional(readOnly = true)
    public ResponseEntity<List<AdminClaimResponse>> getClaims(Principal principal) {
        requireAdmin(principal);
        return ResponseEntity.ok(claimRepository.findAll().stream()
                .map(AdminClaimResponse::from)
                .toList());
    }

    @PostMapping("/users/admins")
    public ResponseEntity<AuthUserResponse> createAdmin(
            Principal principal, @Valid @RequestBody AdminAccountRequest request) {
        requireAdmin(principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(adminAccountService.createAdmin(request));
    }

    @PatchMapping("/users/{id}/toggle")
    public ResponseEntity<AdminUserResponse> toggleUser(
            Principal principal, @PathVariable Long id) {
        requireAdmin(principal);
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        if (user.getRole() == Role.ADMIN) {
            throw new AccessDeniedException("Cannot deactivate an admin account.");
        }
        user.setActive(!user.isActive());
        User saved = userRepository.save(user);
        return ResponseEntity.ok(toUserResponse(saved));
    }

    private void requireAdmin(Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw new BadCredentialsException("Not authenticated");
        }
    }

    private AdminUserResponse toUserResponse(User user) {
        return new AdminUserResponse(
            user.getId(),
            user.getName(),
            user.getEmail(),
            user.getRole(),
            user.isActive(),
            user.getAddress(),
            user.getLatitude(),
            user.getLongitude(),
            user.getCreatedAt() != null ? user.getCreatedAt().toString() : null
        );
    }

    private AdminDonationResponse toDonationResponse(Donation d) {
        String ngoName = claimRepository.findByActiveDonationId(d.getId())
                .map(Claim::getNgo)
                .map(User::getName)
                .orElse(null);
        return new AdminDonationResponse(
            d.getId(),
            d.getFoodName(),
            d.getDonor() != null ? d.getDonor().getName() : null,
            d.getQuantity(),
            d.getStatus(),
            d.getPickupAddress(),
            ngoName,
            d.getSafeUntil()
        );
    }
}
