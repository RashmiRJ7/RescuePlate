package com.foodrescue.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodrescue.config.SecurityConfig;
import com.foodrescue.dto.CancelClaimRequest;
import com.foodrescue.dto.ClaimDonationSummaryResponse;
import com.foodrescue.dto.ClaimResponse;
import com.foodrescue.enums.ClaimStatus;
import com.foodrescue.enums.DonationStatus;
import com.foodrescue.exception.InvalidStatusTransitionException;
import com.foodrescue.exception.ResourceNotFoundException;
import com.foodrescue.service.ClaimService;
import com.foodrescue.service.CustomUserDetailsService;

@WebMvcTest(ClaimController.class)
@Import(SecurityConfig.class)
class ClaimControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ClaimService claimService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    private ClaimResponse sampleClaimResponse(ClaimStatus status, DonationStatus donationStatus) {
        return new ClaimResponse(
                50L,
                7L,
                status,
                Instant.parse("2026-11-20T14:45:00Z"),
                status == ClaimStatus.PICKED_UP || status == ClaimStatus.COMPLETED ? Instant.parse("2026-11-20T15:00:00Z") : null,
                status == ClaimStatus.COMPLETED ? Instant.parse("2026-11-20T15:30:00Z") : null,
                status == ClaimStatus.CANCELLED ? Instant.parse("2026-11-20T14:50:00Z") : null,
                status == ClaimStatus.CANCELLED ? "Vehicle issue" : null,
                new ClaimDonationSummaryResponse(
                        7L,
                        donationStatus,
                        "Club Road, Belagavi",
                        Instant.parse("2026-11-20T18:00:00Z")
                )
        );
    }

    // --- PATCH /api/claims/{id}/cancel ---

    @Test
    @WithMockUser(username = "ngo@example.com", roles = {"NGO"})
    @DisplayName("PATCH /api/claims/{id}/cancel returns 200 on success")
    void cancelClaim_success_returns200() throws Exception {
        ClaimResponse response = sampleClaimResponse(ClaimStatus.CANCELLED, DonationStatus.AVAILABLE);
        when(claimService.cancelClaim(eq(50L), eq("ngo@example.com"), eq("Vehicle issue")))
                .thenReturn(response);

        CancelClaimRequest request = new CancelClaimRequest("Vehicle issue");

        mockMvc.perform(patch("/api/claims/50/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(50))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.donation.status").value("AVAILABLE"));
    }

    @Test
    @WithMockUser(username = "other@example.com", roles = {"NGO"})
    @DisplayName("PATCH /api/claims/{id}/cancel returns 403 when not the owner")
    void cancelClaim_notOwner_returns403() throws Exception {
        when(claimService.cancelClaim(eq(50L), eq("other@example.com"), any()))
                .thenThrow(new AccessDeniedException("Access denied."));

        mockMvc.perform(patch("/api/claims/50/cancel"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @WithMockUser(username = "ngo@example.com", roles = {"NGO"})
    @DisplayName("PATCH /api/claims/{id}/cancel returns 400 when claim is not ACTIVE")
    void cancelClaim_invalidState_returns400() throws Exception {
        when(claimService.cancelClaim(eq(50L), eq("ngo@example.com"), any()))
                .thenThrow(new InvalidStatusTransitionException("Claim cannot be cancelled"));

        mockMvc.perform(patch("/api/claims/50/cancel"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @WithMockUser(username = "donor@example.com", roles = {"DONOR"})
    @DisplayName("PATCH /api/claims/{id}/cancel returns 403 for DONOR role")
    void cancelClaim_asDonor_returns403() throws Exception {
        mockMvc.perform(patch("/api/claims/50/cancel"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PATCH /api/claims/{id}/cancel returns 401 when unauthenticated")
    void cancelClaim_unauthenticated_returns401() throws Exception {
        mockMvc.perform(patch("/api/claims/50/cancel"))
                .andExpect(status().isUnauthorized());
    }

    // --- PATCH /api/claims/{id}/pickup ---

    @Test
    @WithMockUser(username = "ngo@example.com", roles = {"NGO"})
    @DisplayName("PATCH /api/claims/{id}/pickup returns 200 on success")
    void pickupClaim_success_returns200() throws Exception {
        ClaimResponse response = sampleClaimResponse(ClaimStatus.PICKED_UP, DonationStatus.PICKED_UP);
        when(claimService.pickupClaim(50L, "ngo@example.com")).thenReturn(response);

        mockMvc.perform(patch("/api/claims/50/pickup"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(50))
                .andExpect(jsonPath("$.status").value("PICKED_UP"))
                .andExpect(jsonPath("$.donation.status").value("PICKED_UP"));
    }

    @Test
    @WithMockUser(username = "ngo@example.com", roles = {"NGO"})
    @DisplayName("PATCH /api/claims/{id}/pickup returns 400 when claim not in ACTIVE state")
    void pickupClaim_invalidState_returns400() throws Exception {
        when(claimService.pickupClaim(50L, "ngo@example.com"))
                .thenThrow(new InvalidStatusTransitionException("Claim cannot be picked up"));

        mockMvc.perform(patch("/api/claims/50/pickup"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    // --- PATCH /api/claims/{id}/deliver ---

    @Test
    @WithMockUser(username = "ngo@example.com", roles = {"NGO"})
    @DisplayName("PATCH /api/claims/{id}/deliver returns 200 on success")
    void deliverClaim_success_returns200() throws Exception {
        ClaimResponse response = sampleClaimResponse(ClaimStatus.COMPLETED, DonationStatus.DELIVERED);
        when(claimService.deliverClaim(50L, "ngo@example.com")).thenReturn(response);

        mockMvc.perform(patch("/api/claims/50/deliver"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(50))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.donation.status").value("DELIVERED"));
    }

    @Test
    @WithMockUser(username = "ngo@example.com", roles = {"NGO"})
    @DisplayName("PATCH /api/claims/{id}/deliver returns 400 when deliver before pickup")
    void deliverClaim_beforePickup_returns400() throws Exception {
        when(claimService.deliverClaim(50L, "ngo@example.com"))
                .thenThrow(new InvalidStatusTransitionException("Claim must be picked up before delivery"));

        mockMvc.perform(patch("/api/claims/50/deliver"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Claim must be picked up before delivery"));
    }

    // --- GET /api/claims/mine ---

    @Test
    @WithMockUser(username = "ngo@example.com", roles = {"NGO"})
    @DisplayName("GET /api/claims/mine returns list of claims for authenticated NGO")
    void getMyClaims_success_returns200() throws Exception {
        ClaimResponse item1 = sampleClaimResponse(ClaimStatus.ACTIVE, DonationStatus.RESERVED);
        when(claimService.getMyClaims("ngo@example.com", null)).thenReturn(List.of(item1));

        mockMvc.perform(get("/api/claims/mine"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(50))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    @Test
    @WithMockUser(username = "ngo@example.com", roles = {"NGO"})
    @DisplayName("GET /api/claims/mine?status=ACTIVE returns filtered claims")
    void getMyClaims_filtered_returns200() throws Exception {
        ClaimResponse item1 = sampleClaimResponse(ClaimStatus.ACTIVE, DonationStatus.RESERVED);
        when(claimService.getMyClaims("ngo@example.com", ClaimStatus.ACTIVE)).thenReturn(List.of(item1));

        mockMvc.perform(get("/api/claims/mine").param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(50))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    @Test
    @WithMockUser(username = "donor@example.com", roles = {"DONOR"})
    @DisplayName("GET /api/claims/mine returns 403 for DONOR role")
    void getMyClaims_asDonor_returns403() throws Exception {
        mockMvc.perform(get("/api/claims/mine"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/claims/mine returns 401 when unauthenticated")
    void getMyClaims_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/claims/mine"))
                .andExpect(status().isUnauthorized());
    }
}
