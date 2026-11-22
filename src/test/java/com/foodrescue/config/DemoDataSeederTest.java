package com.foodrescue.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.foodrescue.entity.Donation;
import com.foodrescue.entity.DonationStatusHistory;
import com.foodrescue.entity.NgoProfile;
import com.foodrescue.entity.User;
import com.foodrescue.enums.Role;
import com.foodrescue.repository.DonationRepository;
import com.foodrescue.repository.DonationStatusHistoryRepository;
import com.foodrescue.repository.NgoProfileRepository;
import com.foodrescue.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class DemoDataSeederTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private NgoProfileRepository ngoProfileRepository;

    @Mock
    private DonationRepository donationRepository;

    @Mock
    private DonationStatusHistoryRepository historyRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private DemoDataSeeder demoDataSeeder;

    @Test
    @DisplayName("DemoDataSeeder seeds 3 donors, 3 NGOs and 10 donations when DB is fresh")
    void run_seedsAllDataOnFreshDb() {
        when(passwordEncoder.encode(any())).thenReturn("hashedPassword");
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());

        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setId(10L);
            return u;
        });

        when(ngoProfileRepository.findByUserId(any())).thenReturn(Optional.empty());
        when(donationRepository.count()).thenReturn(0L);
        when(donationRepository.save(any(Donation.class))).thenAnswer(i -> {
            Donation d = i.getArgument(0);
            d.setId(100L);
            return d;
        });

        demoDataSeeder.run();

        // 3 donors + 3 NGOs = 6 users saved
        verify(userRepository, times(6)).save(any(User.class));
        // 3 NGO profiles saved
        verify(ngoProfileRepository, times(3)).save(any(NgoProfile.class));
        // 10 donations saved
        verify(donationRepository, times(10)).save(any(Donation.class));
        // 10 initial history logs saved
        verify(historyRepository, times(10)).save(any(DonationStatusHistory.class));
    }
}
