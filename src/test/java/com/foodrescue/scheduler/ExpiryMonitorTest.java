package com.foodrescue.scheduler;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.foodrescue.service.DonationService;

@ExtendWith(MockitoExtension.class)
class ExpiryMonitorTest {

    @Mock
    private DonationService donationService;

    @InjectMocks
    private ExpiryMonitor expiryMonitor;

    @Test
    @DisplayName("monitorExpiry delegates to donationService.expireOverdueDonations")
    void monitorExpiry_delegatesToDonationService() {
        when(donationService.expireOverdueDonations()).thenReturn(3);

        expiryMonitor.monitorExpiry();

        verify(donationService, times(1)).expireOverdueDonations();
    }
}
