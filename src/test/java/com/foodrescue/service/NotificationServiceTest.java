package com.foodrescue.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.foodrescue.entity.Donation;
import com.foodrescue.entity.NgoProfile;
import com.foodrescue.entity.Notification;
import com.foodrescue.entity.User;
import com.foodrescue.enums.FoodCategory;
import com.foodrescue.enums.NotificationType;
import com.foodrescue.enums.Role;
import com.foodrescue.exception.ResourceNotFoundException;
import com.foodrescue.repository.NgoProfileRepository;
import com.foodrescue.repository.NotificationRepository;
import com.foodrescue.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private NgoProfileRepository ngoProfileRepository;

    @Mock
    private NotificationRepository notificationRepository;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(userRepository, ngoProfileRepository, notificationRepository);
    }

    @Test
    void newDonationOnlyNotifiesActiveCategoryMatchingNgosWithinServiceRadius() {
        User nearbyNgo = ngo("nearby@example.com", 15.852, 74.502, true);
        User distantNgo = ngo("distant@example.com", 17.0, 76.0, true);
        User inactiveNgo = ngo("inactive@example.com", 15.852, 74.502, false);
        List<NgoProfile> profiles = List.of(
                new NgoProfile(nearbyNgo, 20, 5.0, "COOKED_MEAL"),
                new NgoProfile(distantNgo, 20, 5.0, "COOKED_MEAL"),
                new NgoProfile(inactiveNgo, 20, 5.0, "COOKED_MEAL"),
                new NgoProfile(ngo("bakery@example.com", 15.852, 74.502, true), 20, 5.0, "BAKERY"));
        when(ngoProfileRepository.findAll()).thenReturn(profiles);

        Donation donation = new Donation();
        donation.setId(42L);
        donation.setFoodName("Fresh rice");
        donation.setCategory(FoodCategory.COOKED_MEAL);
        donation.setQuantity(25);
        donation.setPickupAddress("Central Market");
        donation.setLatitude(15.85);
        donation.setLongitude(74.50);
        donation.setSafeUntil(Instant.now().plusSeconds(3600));

        service.notifyNewDonation(donation);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getRecipient()).isSameAs(nearbyNgo);
        assertThat(saved.getType()).isEqualTo(NotificationType.DONATION_NEW);
        assertThat(saved.getDonationId()).isEqualTo(42L);
        assertThat(saved.getMessage()).contains("25 meals", "Central Market", "minutes");
    }

    @Test
    void markingAnotherUsersNotificationAsReadReturnsNotFound() {
        User recipient = new User("Donor", "donor@example.com", "hash", Role.DONOR);
        recipient.setId(1L);
        when(userRepository.findByEmail("donor@example.com")).thenReturn(Optional.of(recipient));
        when(notificationRepository.findByIdAndRecipientId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRead(99L, "donor@example.com"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private User ngo(String email, double latitude, double longitude, boolean active) {
        User user = new User("Community NGO", email, "hash", Role.NGO);
        user.setId((long) email.hashCode());
        user.setLatitude(latitude);
        user.setLongitude(longitude);
        user.setActive(active);
        return user;
    }
}
