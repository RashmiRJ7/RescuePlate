package com.foodrescue.controller;

import java.security.Principal;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.foodrescue.dto.CancelClaimRequest;
import com.foodrescue.dto.ClaimResponse;
import com.foodrescue.enums.ClaimStatus;
import com.foodrescue.service.ClaimService;

/**
 * Controller handling the lifecycle of food rescue claims (C2, C3, C4).
 */
@RestController
@RequestMapping("/api/claims")
public class ClaimController {

    private final ClaimService claimService;

    public ClaimController(ClaimService claimService) {
        this.claimService = claimService;
    }

    /**
     * Cancel an active claim (C2).
     */
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<ClaimResponse> cancelClaim(
            @PathVariable Long id,
            @RequestBody(required = false) CancelClaimRequest request,
            Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw new BadCredentialsException("Not authenticated");
        }
        String reason = request != null ? request.reason() : null;
        ClaimResponse response = claimService.cancelClaim(id, principal.getName(), reason);
        return ResponseEntity.ok(response);
    }

    /**
     * Mark an active claim as picked up (C3).
     */
    @PatchMapping("/{id}/pickup")
    public ResponseEntity<ClaimResponse> pickupClaim(
            @PathVariable Long id,
            Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw new BadCredentialsException("Not authenticated");
        }
        ClaimResponse response = claimService.pickupClaim(id, principal.getName());
        return ResponseEntity.ok(response);
    }

    /**
     * Mark a picked-up claim as delivered / completed (C3).
     */
    @PatchMapping("/{id}/deliver")
    public ResponseEntity<ClaimResponse> deliverClaim(
            @PathVariable Long id,
            Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw new BadCredentialsException("Not authenticated");
        }
        ClaimResponse response = claimService.deliverClaim(id, principal.getName());
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieve claims owned by the authenticated NGO (C4).
     */
    @GetMapping("/mine")
    public ResponseEntity<List<ClaimResponse>> getMyClaims(
            @RequestParam(required = false) ClaimStatus status,
            Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw new BadCredentialsException("Not authenticated");
        }
        List<ClaimResponse> response = claimService.getMyClaims(principal.getName(), status);
        return ResponseEntity.ok(response);
    }
}
