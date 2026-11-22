package com.foodrescue.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.foodrescue.service.DonationService;

/**
 * Scheduled background job monitoring and expiring overdue food donations (Task S1).
 * <p>
 * Runs periodically with a fixed delay of 60 seconds (60_000 ms).
 * Avoids the self-invocation proxy trap by delegating transactional execution to {@link DonationService}.
 * </p>
 */
@Component
public class ExpiryMonitor {

    private static final Logger log = LoggerFactory.getLogger(ExpiryMonitor.class);

    private final DonationService donationService;

    public ExpiryMonitor(DonationService donationService) {
        this.donationService = donationService;
    }

    /**
     * Executes the overdue food expiration scan.
     */
    @Scheduled(fixedDelay = 60_000)
    public void monitorExpiry() {
        int expiredCount = donationService.expireOverdueDonations();
        if (expiredCount > 0) {
            log.info("ExpiryMonitor: expired {} overdue donation(s).", expiredCount);
        }
    }
}
