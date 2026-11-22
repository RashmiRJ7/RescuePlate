package com.foodrescue.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.foodrescue.dto.NgoProfileResponse;
import com.foodrescue.dto.NgoProfileUpdateRequest;
import com.foodrescue.entity.NgoProfile;
import com.foodrescue.entity.User;
import com.foodrescue.enums.FoodCategory;
import com.foodrescue.enums.Role;
import com.foodrescue.exception.ResourceNotFoundException;
import com.foodrescue.repository.NgoProfileRepository;
import com.foodrescue.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class NgoProfileServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private NgoProfileRepository ngoProfileRepository;

    private NgoProfileService ngoProfileService;

    @BeforeEach
    void setUp() {
        ngoProfileService = new NgoProfileService(userRepository, ngoProfileRepository);
    }

    @Test
    void getProfileSuccess() {
        User user = new User("Hope Shelter", "hope@ngo.example", "hash", Role.NGO);
        user.setId(5L);

        NgoProfile profile = new NgoProfile(user, 120, 10.0, "COOKED_MEAL,BAKERY");
        profile.setId(2L);

        when(userRepository.findByEmail("hope@ngo.example")).thenReturn(Optional.of(user));
        when(ngoProfileRepository.findByUserId(5L)).thenReturn(Optional.of(profile));

        NgoProfileResponse response = ngoProfileService.getProfile("hope@ngo.example");

        assertNotNull(response);
        assertEquals(120, response.capacityMeals());
        assertEquals(10.0, response.serviceRadiusKm());
        assertEquals(2, response.acceptedCategories().size());
    }

    @Test
    void updateProfileSuccess() {
        User user = new User("Hope Shelter", "hope@ngo.example", "hash", Role.NGO);
        user.setId(5L);

        NgoProfile profile = new NgoProfile(user, 120, 10.0, "COOKED_MEAL");
        profile.setId(2L);

        when(userRepository.findByEmail("hope@ngo.example")).thenReturn(Optional.of(user));
        when(ngoProfileRepository.findByUserId(5L)).thenReturn(Optional.of(profile));
        when(ngoProfileRepository.save(any(NgoProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        NgoProfileUpdateRequest updateRequest = new NgoProfileUpdateRequest(
                200,
                20.0,
                Set.of(FoodCategory.COOKED_MEAL, FoodCategory.PACKAGED)
        );

        NgoProfileResponse response = ngoProfileService.updateProfile("hope@ngo.example", updateRequest);

        assertNotNull(response);
        assertEquals(200, response.capacityMeals());
        assertEquals(20.0, response.serviceRadiusKm());
        assertEquals(2, response.acceptedCategories().size());
        verify(ngoProfileRepository).save(profile);
    }

    @Test
    void getProfileNotFoundThrowsResourceNotFoundException() {
        when(userRepository.findByEmail("missing@ngo.example")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> ngoProfileService.getProfile("missing@ngo.example"));
    }
}
