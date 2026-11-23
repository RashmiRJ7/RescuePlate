package com.foodrescue.workflow;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodrescue.entity.Donation;
import com.foodrescue.enums.DonationStatus;
import com.foodrescue.repository.DonationRepository;
import com.foodrescue.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("workflow-test")
class RescuePlateWorkflowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DonationRepository donationRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void completesDonorNgoAdminWorkflowWithPrivateNotifications() throws Exception {
        mockMvc.perform(get("/api/auth/admin-setup/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));

        String initialAdminResponse = mockMvc.perform(post("/api/auth/admin-setup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", "Workflow Administrator",
                                "email", "admin@workflow.example",
                                "password", "AdminWorkflowPassword1!",
                                "confirmPassword", "AdminWorkflowPassword1!",
                                "phone", "9876543210",
                                "setupKey", "workflow-test-setup-key"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(initialAdminResponse).doesNotContain("workflow-test-setup-key");

        mockMvc.perform(get("/api/auth/admin-setup/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));

        register("donor@workflow.example", "DONOR", 15.85, 74.50);
        register("ngo@workflow.example", "NGO", 15.852, 74.502);
        MockHttpSession donorSession = login("donor@workflow.example", "DonorWorkflowPassword1!");
        MockHttpSession ngoSession = login("ngo@workflow.example", "NgoWorkflowPassword1!");

        mockMvc.perform(get("/api/donations/feed").session(ngoSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        long firstDonationId = createDonation(donorSession, "Fresh rice");
        assertThat(donationRepository.count()).isEqualTo(1);
        Donation persisted = donationRepository.findById(firstDonationId).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(DonationStatus.AVAILABLE);

        mockMvc.perform(get("/api/donations/feed").session(ngoSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(firstDonationId));
        mockMvc.perform(get("/api/notifications").session(ngoSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("DONATION_NEW"));

        long firstClaimId = claim(ngoSession, firstDonationId);
        mockMvc.perform(get("/api/notifications").session(donorSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("CLAIM_CREATED"));
        mockMvc.perform(patch("/api/claims/{id}/pickup", firstClaimId).session(ngoSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PICKED_UP"));
        mockMvc.perform(patch("/api/claims/{id}/deliver", firstClaimId).session(ngoSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        mockMvc.perform(get("/api/notifications").session(donorSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("CLAIM_DELIVERED"));
        mockMvc.perform(get("/api/notifications").session(ngoSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("CLAIM_DELIVERED"));

        long secondDonationId = createDonation(donorSession, "Packaged meals");
        long secondClaimId = claim(ngoSession, secondDonationId);
        mockMvc.perform(patch("/api/claims/{id}/cancel", secondClaimId)
                        .session(ngoSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "Vehicle unavailable"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(donationRepository.findById(secondDonationId).orElseThrow().getStatus())
                .isEqualTo(DonationStatus.AVAILABLE);

        mockMvc.perform(get("/api/notifications").session(donorSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("CLAIM_CANCELLED"));
        mockMvc.perform(get("/api/notifications").session(ngoSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("CLAIM_CANCELLED"));

        MockHttpSession adminSession = login("admin@workflow.example", "AdminWorkflowPassword1!");
        mockMvc.perform(get("/api/admin/stats").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").value(3))
                .andExpect(jsonPath("$.totalDonations").value(2));
        mockMvc.perform(get("/api/admin/users").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        mockMvc.perform(get("/api/admin/donations").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get("/api/admin/claims").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").exists());
        Long donorId = userRepository.findByEmail("donor@workflow.example").orElseThrow().getId();
        mockMvc.perform(patch("/api/admin/users/{id}/toggle", donorId).session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        mockMvc.perform(patch("/api/admin/users/{id}/toggle", donorId).session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
        mockMvc.perform(post("/api/admin/users/admins")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", "Second Administrator",
                                "email", "admin2@workflow.example",
                                "password", "SecondAdminPassword1!"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ADMIN"));
        mockMvc.perform(post("/api/admin/users/admins")
                        .session(donorSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", "Unauthorized Administrator",
                                "email", "intruder@workflow.example",
                                "password", "UnauthorizedPassword1!"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/notifications/read-all").session(donorSession))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/notifications/unread-count").session(donorSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(0));
    }

    private void register(String email, String role, double latitude, double longitude) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", role.equals("NGO") ? "Workflow NGO" : "Workflow Donor",
                                "email", email,
                                "password", role.equals("NGO") ? "NgoWorkflowPassword1!" : "DonorWorkflowPassword1!",
                                "role", role,
                                "phone", "9876543210",
                                "address", "Central Market, Belagavi",
                                "latitude", latitude,
                                "longitude", longitude))))
                .andExpect(status().isCreated());
    }

    private MockHttpSession login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private long createDonation(MockHttpSession session, String foodName) throws Exception {
        String response = mockMvc.perform(post("/api/donations")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "foodName", foodName,
                                "category", "COOKED_MEAL",
                                "quantity", 25,
                                "description", "Freshly prepared and packed safely.",
                                "pickupAddress", "Central Market, Belagavi",
                                "latitude", 15.85,
                                "longitude", 74.50,
                                "readyFrom", Instant.now().minus(1, ChronoUnit.MINUTES).toString(),
                                "safeUntil", Instant.now().plus(3, ChronoUnit.HOURS).toString()))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode donation = objectMapper.readTree(response);
        return donation.path("id").asLong();
    }

    private long claim(MockHttpSession session, long donationId) throws Exception {
        String response = mockMvc.perform(post("/api/donations/{id}/claim", donationId)
                        .session(session))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).path("id").asLong();
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
