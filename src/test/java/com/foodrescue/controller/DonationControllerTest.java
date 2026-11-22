package com.foodrescue.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.foodrescue.config.SecurityConfig;
import com.foodrescue.dto.ClaimDonationSummaryResponse;
import com.foodrescue.dto.ClaimResponse;
import com.foodrescue.dto.CreateDonationRequest;
import com.foodrescue.dto.DonationFeedResponse;
import com.foodrescue.dto.DonationHistoryResponse;
import com.foodrescue.dto.DonationResponse;
import com.foodrescue.entity.User;
import com.foodrescue.enums.ClaimStatus;
import com.foodrescue.enums.DonationStatus;
import com.foodrescue.enums.FoodCategory;
import com.foodrescue.enums.Role;
import com.foodrescue.exception.DonationNotClaimableException;
import com.foodrescue.exception.NgoNotEligibleException;
import com.foodrescue.exception.ResourceNotFoundException;
import com.foodrescue.exception.ValidationException;
import com.foodrescue.repository.UserRepository;
import com.foodrescue.service.ClaimService;
import com.foodrescue.service.CustomUserDetailsService;
import com.foodrescue.service.DonationService;
import com.foodrescue.service.FeedService;

@WebMvcTest(DonationController.class)
@Import(SecurityConfig.class)
class DonationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DonationService donationService;

    @MockitoBean
    private FeedService feedService;

    @MockitoBean
    private ClaimService claimService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    @WithMockUser(username = "donor@example.com", roles = {"DONOR"})
    void postDonationSuccessReturns201() throws Exception {
        DonationResponse response = new DonationResponse(
                7L,
                "Veg biryani packets",
                FoodCategory.COOKED_MEAL,
                50,
                "Warm food",
                DonationStatus.AVAILABLE,
                "Club Road, Belagavi",
                15.8497,
                74.4977,
                Instant.now(),
                Instant.now().plusSeconds(14400),
                240L,
                Instant.now(),
                Instant.now(),
                null
        );

        when(donationService.createDonation(any(CreateDonationRequest.class), eq("donor@example.com")))
                .thenReturn(response);

        String body = """
                {
                  "foodName": "Veg biryani packets",
                  "category": "COOKED_MEAL",
                  "quantity": 50,
                  "pickupAddress": "Club Road, Belagavi",
                  "latitude": 15.8497,
                  "longitude": 74.4977,
                  "safeUntil": "2026-11-22T18:00:00Z"
                }
                """;

        mockMvc.perform(post("/api/donations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.foodName").value("Veg biryani packets"))
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.latitude").value(15.8497))
                .andExpect(jsonPath("$.longitude").value(74.4977));
    }

    @Test
    @WithMockUser(username = "donor@example.com", roles = {"DONOR"})
    void postDonationValidationFailureReturns400() throws Exception {
        when(donationService.createDonation(any(CreateDonationRequest.class), eq("donor@example.com")))
                .thenThrow(new ValidationException(Map.of("safeUntil", "must be at least 30 minutes from now")));

        String body = """
                {
                  "foodName": "Veg biryani packets",
                  "category": "COOKED_MEAL",
                  "quantity": 50,
                  "pickupAddress": "Club Road, Belagavi",
                  "safeUntil": "2026-11-22T07:25:00Z"
                }
                """;

        mockMvc.perform(post("/api/donations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.fieldErrors.safeUntil").value("must be at least 30 minutes from now"));
    }

    @Test
    @WithMockUser(username = "ngo@example.com", roles = {"NGO"})
    void postDonationAsNgoReturns403() throws Exception {
        String body = """
                {
                  "foodName": "Veg biryani packets",
                  "category": "COOKED_MEAL",
                  "quantity": 50,
                  "pickupAddress": "Club Road, Belagavi",
                  "safeUntil": "2026-11-22T18:00:00Z"
                }
                """;

        mockMvc.perform(post("/api/donations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @WithMockUser(username = "donor@example.com", roles = {"DONOR"})
    void getMyDonationsSuccessReturns200() throws Exception {
        DonationResponse d1 = new DonationResponse(
                7L,
                "Veg biryani packets",
                FoodCategory.COOKED_MEAL,
                50,
                null,
                DonationStatus.AVAILABLE,
                "Club Road",
                15.84,
                74.49,
                Instant.now(),
                Instant.now().plusSeconds(14400),
                240L,
                Instant.now(),
                Instant.now(),
                null
        );

        when(donationService.getMyDonations("donor@example.com", null))
                .thenReturn(List.of(d1));

        mockMvc.perform(get("/api/donations/mine"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(7))
                .andExpect(jsonPath("$[0].status").value("AVAILABLE"));
    }

    @Test
    @WithMockUser(username = "ngo@example.com", roles = {"NGO"})
    void getMyDonationsAsNgoReturns403() throws Exception {
        mockMvc.perform(get("/api/donations/mine"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @WithMockUser(username = "donor1@example.com", roles = {"DONOR"})
    void getDonationByIdSuccess() throws Exception {
        User donor1 = new User("Donor 1", "donor1@example.com", "hash", Role.DONOR);
        donor1.setId(1L);

        when(userRepository.findByEmail("donor1@example.com")).thenReturn(Optional.of(donor1));

        DonationResponse response = new DonationResponse(
                7L,
                "Veg biryani packets",
                FoodCategory.COOKED_MEAL,
                50,
                null,
                DonationStatus.AVAILABLE,
                "Club Road",
                15.84,
                74.49,
                Instant.now(),
                Instant.now().plusSeconds(14400),
                240L,
                Instant.now(),
                Instant.now(),
                null
        );

        when(donationService.getDonationById(7L, donor1)).thenReturn(response);

        mockMvc.perform(get("/api/donations/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7));
    }

    @Test
    @WithMockUser(username = "donor2@example.com", roles = {"DONOR"})
    void getDonationByIdForbiddenWhenNotOwner() throws Exception {
        User donor2 = new User("Donor 2", "donor2@example.com", "hash", Role.DONOR);
        donor2.setId(2L);

        when(userRepository.findByEmail("donor2@example.com")).thenReturn(Optional.of(donor2));
        when(donationService.getDonationById(7L, donor2)).thenThrow(new AccessDeniedException("Access denied."));

        mockMvc.perform(get("/api/donations/7"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @WithMockUser(username = "donor1@example.com", roles = {"DONOR"})
    void getDonationByIdNotFoundReturns404() throws Exception {
        User donor1 = new User("Donor 1", "donor1@example.com", "hash", Role.DONOR);
        donor1.setId(1L);

        when(userRepository.findByEmail("donor1@example.com")).thenReturn(Optional.of(donor1));
        when(donationService.getDonationById(99L, donor1))
                .thenThrow(new ResourceNotFoundException("Donation not found with id: 99"));

        mockMvc.perform(get("/api/donations/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @WithMockUser(username = "donor1@example.com", roles = {"DONOR"})
    void getDonationHistorySuccess() throws Exception {
        User donor1 = new User("Donor 1", "donor1@example.com", "hash", Role.DONOR);
        donor1.setId(1L);

        when(userRepository.findByEmail("donor1@example.com")).thenReturn(Optional.of(donor1));

        DonationHistoryResponse h1 = new DonationHistoryResponse(
                null,
                DonationStatus.AVAILABLE,
                "Donor 1",
                "Donation posted",
                Instant.now()
        );
        DonationHistoryResponse h2 = new DonationHistoryResponse(
                DonationStatus.AVAILABLE,
                DonationStatus.EXPIRED,
                "System",
                "Safe until passed",
                Instant.now()
        );

        when(donationService.getDonationHistory(7L, donor1)).thenReturn(List.of(h1, h2));

        mockMvc.perform(get("/api/donations/7/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].changedBy").value("Donor 1"))
                .andExpect(jsonPath("$[0].note").value("Donation posted"))
                .andExpect(jsonPath("$[1].changedBy").value("System"));
    }

    @Test
    @WithMockUser(username = "ngo@example.com", roles = {"NGO"})
    void getFeedSuccessReturns200() throws Exception {
        DonationFeedResponse item = new DonationFeedResponse(
                7L,
                "Veg biryani packets",
                FoodCategory.COOKED_MEAL,
                50,
                "Hotel Sunrise",
                "Club Road, Belagavi",
                2.4,
                28L,
                Instant.now().plusSeconds(1680),
                95.0,
                false,
                DonationStatus.AVAILABLE
        );

        when(feedService.getFeed(eq("ngo@example.com"), any(), any(), any(), any(), any()))
                .thenReturn(List.of(item));

        mockMvc.perform(get("/api/donations/feed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(7))
                .andExpect(jsonPath("$[0].urgencyScore").value(95.0))
                .andExpect(jsonPath("$[0].donorName").value("Hotel Sunrise"))
                .andExpect(jsonPath("$[0].distanceKm").value(2.4));
    }

    @Test
    @WithMockUser(username = "donor@example.com", roles = {"DONOR"})
    void getFeedAsDonorReturns403() throws Exception {
        mockMvc.perform(get("/api/donations/feed"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void getFeedUnauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/donations/feed"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @WithMockUser(username = "ngo@example.com", roles = {"NGO"})
    void claimDonation_success_returns201() throws Exception {
        ClaimResponse claimResponse = new ClaimResponse(
                31L,
                7L,
                ClaimStatus.ACTIVE,
                Instant.parse("2026-11-20T14:45:00Z"),
                new ClaimDonationSummaryResponse(
                        7L,
                        DonationStatus.RESERVED,
                        "Club Road, Belagavi",
                        Instant.parse("2026-11-20T18:00:00Z")
                )
        );

        when(claimService.claim(7L, "ngo@example.com")).thenReturn(claimResponse);

        mockMvc.perform(post("/api/donations/7/claim"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(31))
                .andExpect(jsonPath("$.donationId").value(7))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.donation.id").value(7))
                .andExpect(jsonPath("$.donation.status").value("RESERVED"))
                .andExpect(jsonPath("$.donation.pickupAddress").value("Club Road, Belagavi"));
    }

    @Test
    @WithMockUser(username = "donor@example.com", roles = {"DONOR"})
    void claimDonation_asDonor_returns403() throws Exception {
        mockMvc.perform(post("/api/donations/7/claim"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void claimDonation_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/donations/7/claim"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @WithMockUser(username = "ngo@example.com", roles = {"NGO"})
    void claimDonation_conflict_returns409() throws Exception {
        when(claimService.claim(7L, "ngo@example.com"))
                .thenThrow(new DonationNotClaimableException("Donation is no longer available."));

        mockMvc.perform(post("/api/donations/7/claim"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Donation is no longer available."));
    }

    @Test
    @WithMockUser(username = "ngo@example.com", roles = {"NGO"})
    void claimDonation_ineligible_returns400() throws Exception {
        when(claimService.claim(7L, "ngo@example.com"))
                .thenThrow(new NgoNotEligibleException("Donation is outside NGO service radius"));

        mockMvc.perform(post("/api/donations/7/claim"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Donation is outside NGO service radius"));
    }

    @Test
    @WithMockUser(username = "ngo@example.com", roles = {"NGO"})
    void claimDonation_notFound_returns404() throws Exception {
        when(claimService.claim(999L, "ngo@example.com"))
                .thenThrow(new ResourceNotFoundException("Donation not found with id: 999"));

        mockMvc.perform(post("/api/donations/999/claim"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
