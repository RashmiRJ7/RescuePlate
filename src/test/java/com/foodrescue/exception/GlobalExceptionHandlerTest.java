package com.foodrescue.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TestErrorController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void resourceNotFoundReturns404() throws Exception {
        mockMvc.perform(get("/test-errors/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Donation not found with id: 99"))
                .andExpect(jsonPath("$.path").value("/test-errors/not-found"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void donationNotClaimableReturns409() throws Exception {
        mockMvc.perform(get("/test-errors/not-claimable"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Donation is no longer available."))
                .andExpect(jsonPath("$.path").value("/test-errors/not-claimable"));
    }

    @Test
    void optimisticLockingConflictReturns409() throws Exception {
        mockMvc.perform(get("/test-errors/concurrency"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("This donation has already been claimed by another NGO."));
    }

    @Test
    void ngoNotEligibleReturns400() throws Exception {
        mockMvc.perform(get("/test-errors/not-eligible"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Category BAKERY is not accepted by this NGO."));
    }

    @Test
    void invalidStatusTransitionReturns400() throws Exception {
        mockMvc.perform(get("/test-errors/invalid-transition"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Illegal status transition from AVAILABLE to DELIVERED"));
    }

    @Test
    void validationErrorReturns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/test-errors/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\", \"quantity\": 0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.quantity").exists());
    }

    // Dummy test controller to trigger exceptions
    @RestController
    @RequestMapping("/test-errors")
    static class TestErrorController {

        @GetMapping("/not-found")
        public void throwNotFound() {
            throw new ResourceNotFoundException("Donation", 99L);
        }

        @GetMapping("/not-claimable")
        public void throwNotClaimable() {
            throw new DonationNotClaimableException();
        }

        @GetMapping("/concurrency")
        public void throwConcurrency() {
            throw new OptimisticLockingFailureException("Simulated lock conflict");
        }

        @GetMapping("/not-eligible")
        public void throwNotEligible() {
            throw new NgoNotEligibleException("Category BAKERY is not accepted by this NGO.");
        }

        @GetMapping("/invalid-transition")
        public void throwInvalidTransition() {
            throw new InvalidStatusTransitionException("AVAILABLE", "DELIVERED");
        }

        @PostMapping("/validation")
        public void validateBody(@Valid @RequestBody DummyPayload payload) {
        }
    }

    record DummyPayload(
            @NotBlank(message = "Name cannot be blank") String name,
            @Min(value = 1, message = "Quantity must be at least 1") int quantity
    ) {}
}
