package com.foodrescue.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.foodrescue.controller.HealthController;
import com.foodrescue.service.CustomUserDetailsService;

/**
 * End-to-end security rule verification per ARCHITECTURE.md §7 and BACKEND_ROADMAP.md Step 5.
 * Uses WebMvcTest so tests are isolated and do not depend on a live MySQL database.
 */
@WebMvcTest(controllers = {HealthController.class})
@Import({SecurityConfig.class, SecurityConfigTest.TestSecurityController.class})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void publicEndpointsAreAccessibleWithoutLogin() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk());
    }

    @Test
    void unauthenticatedRequestToProtectedEndpointReturnsJson401() throws Exception {
        mockMvc.perform(get("/api/donations/mine"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.path").value("/api/donations/mine"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @WithMockUser(username = "donor@example.com", roles = {"DONOR"})
    void donorCallingNgoOnlyEndpointReturnsJson403() throws Exception {
        mockMvc.perform(get("/api/donations/feed"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("Access denied."))
                .andExpect(jsonPath("$.path").value("/api/donations/feed"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @WithMockUser(username = "donor@example.com", roles = {"DONOR"})
    void donorCallingAdminEndpointReturnsJson403() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("Access denied."));
    }

    @Test
    @WithMockUser(username = "ngo@example.com", roles = {"NGO"})
    void ngoCallingDonorOnlyPostDonationReturnsJson403() throws Exception {
        mockMvc.perform(post("/api/donations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("Access denied."));
    }

    @Test
    @WithMockUser(username = "ngo@example.com", roles = {"NGO"})
    void ngoCallingNgoOnlyEndpointPassesSecurity() throws Exception {
        mockMvc.perform(get("/api/donations/feed"))
                .andExpect(status().isOk())
                .andExpect(content().string("feed-ok"));
    }

    @Test
    @WithMockUser(username = "ngo@example.com", roles = {"NGO"})
    void ngoCallingClaimCancelPassesSecurity() throws Exception {
        mockMvc.perform(patch("/api/claims/1/cancel"))
                .andExpect(status().isOk())
                .andExpect(content().string("cancelled-ok"));
    }

    @Test
    @WithMockUser(username = "donor@example.com", roles = {"DONOR"})
    void donorCallingClaimCancelReturnsJson403() throws Exception {
        mockMvc.perform(patch("/api/claims/1/cancel"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = {"ADMIN"})
    void adminCallingAdminEndpointPassesSecurity() throws Exception {
        mockMvc.perform(get("/api/admin/donations"))
                .andExpect(status().isOk())
                .andExpect(content().string("admin-donations-ok"));
    }

    @Test
    @WithMockUser(username = "donor@example.com", roles = {"DONOR"})
    void donorCallingDonorOnlyEndpointPassesSecurity() throws Exception {
        mockMvc.perform(get("/api/donations/mine"))
                .andExpect(status().isOk())
                .andExpect(content().string("my-donations-ok"));
    }

    /**
     * Dummy test endpoints used exclusively to verify that path security rules match
     * ARCHITECTURE.md §7.
     */
    @RestController
    static class TestSecurityController {

        @GetMapping("/api/donations/mine")
        public String myDonations() {
            return "my-donations-ok";
        }

        @PostMapping("/api/donations")
        public String createDonation() {
            return "created-ok";
        }

        @GetMapping("/api/donations/feed")
        public String feed() {
            return "feed-ok";
        }

        @PatchMapping("/api/claims/{id}/cancel")
        public String cancelClaim(@PathVariable Long id) {
            return "cancelled-ok";
        }

        @GetMapping("/api/claims/mine")
        public String myClaims() {
            return "claims-ok";
        }

        @GetMapping("/api/admin/users")
        public String adminUsers() {
            return "admin-users-ok";
        }

        @GetMapping("/api/admin/donations")
        public String adminDonations() {
            return "admin-donations-ok";
        }
    }
}
