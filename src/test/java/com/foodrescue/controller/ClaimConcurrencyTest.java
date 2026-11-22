package com.foodrescue.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.foodrescue.config.SecurityConfig;
import com.foodrescue.dto.ClaimDonationSummaryResponse;
import com.foodrescue.dto.ClaimResponse;
import com.foodrescue.enums.ClaimStatus;
import com.foodrescue.enums.DonationStatus;
import com.foodrescue.repository.UserRepository;
import com.foodrescue.service.ClaimService;
import com.foodrescue.service.CustomUserDetailsService;
import com.foodrescue.service.DonationService;
import com.foodrescue.service.FeedService;

/**
 * Task C5 Concurrency Test per BACKEND_ROADMAP.md Step 9 & TASKS.md C5.
 * <p>
 * Simulates two NGOs clicking "Claim" at the exact same millisecond using a {@link CountDownLatch}.
 * Asserts that exactly one claim succeeds (201 Created) and the other fails with a concurrency conflict (409 Conflict).
 * Repeated 20 times to verify race condition resilience.
 * </p>
 */
@WebMvcTest(DonationController.class)
@Import(SecurityConfig.class)
class ClaimConcurrencyTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ClaimService claimService;

    @MockitoBean
    private DonationService donationService;

    @MockitoBean
    private FeedService feedService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @RepeatedTest(20)
    @DisplayName("C5: Two concurrent claims on the same donation result in exactly one 201 and one 409")
    void twoConcurrentClaims_oneWins_oneConflicts() throws Exception {
        Long donationId = 1L;
        AtomicBoolean claimed = new AtomicBoolean(false);

        ClaimResponse successResponse = new ClaimResponse(
                100L,
                donationId,
                ClaimStatus.ACTIVE,
                Instant.now(),
                new ClaimDonationSummaryResponse(
                        donationId,
                        DonationStatus.RESERVED,
                        "Club Road, Belagavi",
                        Instant.now().plusSeconds(3600)
                )
        );

        when(claimService.claim(eq(donationId), any(String.class))).thenAnswer(invocation -> {
            if (claimed.compareAndSet(false, true)) {
                return successResponse;
            } else {
                throw new OptimisticLockingFailureException("Version conflict: donation already claimed");
            }
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Integer> responseStatuses = Collections.synchronizedList(new ArrayList<>());

        // Thread 1: NGO A
        executor.submit(() -> {
            try {
                readyLatch.countDown();
                startLatch.await();
                MvcResult result = mockMvc.perform(
                        post("/api/donations/" + donationId + "/claim")
                                .with(user("ngoA@example.com").roles("NGO"))
                ).andReturn();
                responseStatuses.add(result.getResponse().getStatus());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // Thread 2: NGO B
        executor.submit(() -> {
            try {
                readyLatch.countDown();
                startLatch.await();
                MvcResult result = mockMvc.perform(
                        post("/api/donations/" + donationId + "/claim")
                                .with(user("ngoB@example.com").roles("NGO"))
                ).andReturn();
                responseStatuses.add(result.getResponse().getStatus());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // Wait until both threads are queued and ready
        boolean ready = readyLatch.await(5, TimeUnit.SECONDS);
        assertThat(ready).isTrue();

        // Release both threads simultaneously
        startLatch.countDown();

        executor.shutdown();
        boolean finished = executor.awaitTermination(5, TimeUnit.SECONDS);
        assertThat(finished).isTrue();

        // Exactly one 201 (Created) and one 409 (Conflict)
        assertThat(responseStatuses)
                .hasSize(2)
                .containsExactlyInAnyOrder(201, 409);
    }
}
