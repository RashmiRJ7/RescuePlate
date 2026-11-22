package com.foodrescue.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.foodrescue.config.SecurityConfig;
import com.foodrescue.dto.NgoProfileResponse;
import com.foodrescue.dto.NgoProfileUpdateRequest;
import com.foodrescue.enums.FoodCategory;
import com.foodrescue.service.CustomUserDetailsService;
import com.foodrescue.service.NgoProfileService;

@WebMvcTest(NgoProfileController.class)
@Import(SecurityConfig.class)
class NgoProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NgoProfileService ngoProfileService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    @WithMockUser(username = "ngo@example.com", roles = {"NGO"})
    void getNgoProfileAsNgoReturns200() throws Exception {
        when(ngoProfileService.getProfile("ngo@example.com"))
                .thenReturn(new NgoProfileResponse(120, 10.0, Set.of(FoodCategory.COOKED_MEAL, FoodCategory.BAKERY), 0, 0, null));

        mockMvc.perform(get("/api/ngo/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capacityMeals").value(120))
                .andExpect(jsonPath("$.serviceRadiusKm").value(10.0))
                .andExpect(jsonPath("$.totalClaims").value(0))
                .andExpect(jsonPath("$.completedClaims").value(0))
                .andExpect(jsonPath("$.reliabilityPercent").doesNotExist());
    }

    @Test
    @WithMockUser(username = "ngo@example.com", roles = {"NGO"})
    void updateNgoProfileAsNgoReturns200() throws Exception {
        when(ngoProfileService.updateProfile(eq("ngo@example.com"), any(NgoProfileUpdateRequest.class)))
                .thenReturn(new NgoProfileResponse(150, 15.0, Set.of(FoodCategory.COOKED_MEAL), 0, 0, null));

        String body = """
                {
                  "capacityMeals": 150,
                  "serviceRadiusKm": 15.0,
                  "acceptedCategories": ["COOKED_MEAL"]
                }
                """;

        mockMvc.perform(put("/api/ngo/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capacityMeals").value(150))
                .andExpect(jsonPath("$.serviceRadiusKm").value(15.0));
    }

    @Test
    @WithMockUser(username = "donor@example.com", roles = {"DONOR"})
    void getNgoProfileAsDonorReturns403() throws Exception {
        mockMvc.perform(get("/api/ngo/profile"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    @WithMockUser(username = "donor@example.com", roles = {"DONOR"})
    void putNgoProfileAsDonorReturns403() throws Exception {
        String body = """
                {
                  "capacityMeals": 150,
                  "serviceRadiusKm": 15.0,
                  "acceptedCategories": ["COOKED_MEAL"]
                }
                """;

        mockMvc.perform(put("/api/ngo/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void getNgoProfileUnauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/ngo/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }
}
