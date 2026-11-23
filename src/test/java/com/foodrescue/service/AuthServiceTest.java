package com.foodrescue.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;

import com.foodrescue.dto.AuthUserResponse;
import com.foodrescue.dto.LoginRequest;
import com.foodrescue.dto.LoginResponse;
import com.foodrescue.dto.NgoProfileRegisterRequest;
import com.foodrescue.dto.RegisterRequest;
import com.foodrescue.dto.UserProfileResponse;
import com.foodrescue.entity.NgoProfile;
import com.foodrescue.entity.User;
import com.foodrescue.enums.FoodCategory;
import com.foodrescue.enums.Role;
import com.foodrescue.exception.DuplicateResourceException;
import com.foodrescue.repository.NgoProfileRepository;
import com.foodrescue.repository.UserRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private NgoProfileRepository ngoProfileRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private SecurityContextRepository securityContextRepository;

    @Mock
    private HttpServletRequest httpRequest;

    @Mock
    private HttpServletResponse httpResponse;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                ngoProfileRepository,
                passwordEncoder,
                authenticationManager,
                securityContextRepository
        );
    }

    @Test
    void registerDonorSuccess() {
        RegisterRequest request = new RegisterRequest(
                "Hotel Sunrise",
                "kitchen@sunrise.example",
                "Str0ngPass!",
                Role.DONOR,
                "9876543210",
                "Club Road",
                15.84,
                74.49,
                null
        );

        when(userRepository.existsByEmail("kitchen@sunrise.example")).thenReturn(false);
        when(passwordEncoder.encode("Str0ngPass!")).thenReturn("encodedPassword");

        User savedUser = new User("Hotel Sunrise", "kitchen@sunrise.example", "encodedPassword", Role.DONOR);
        savedUser.setId(10L);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        AuthUserResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals(10L, response.id());
        assertEquals("Hotel Sunrise", response.name());
        assertEquals("kitchen@sunrise.example", response.email());
        assertEquals(Role.DONOR, response.role());
    }

    @Test
    void registerNgoSuccess() {
        NgoProfileRegisterRequest profileRequest = new NgoProfileRegisterRequest(
                120,
                10.0,
                Set.of(FoodCategory.COOKED_MEAL, FoodCategory.BAKERY)
        );
        RegisterRequest request = new RegisterRequest(
                "Food Shelter",
                "shelter@ngo.example",
                "Str0ngPass!",
                Role.NGO,
                "9876543210",
                "Main Road",
                15.84,
                74.49,
                profileRequest
        );

        when(userRepository.existsByEmail("shelter@ngo.example")).thenReturn(false);
        when(passwordEncoder.encode("Str0ngPass!")).thenReturn("encodedPassword");

        User savedUser = new User("Food Shelter", "shelter@ngo.example", "encodedPassword", Role.NGO);
        savedUser.setId(20L);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        NgoProfile savedProfile = new NgoProfile(savedUser, 120, 10.0, "COOKED_MEAL,BAKERY");
        savedProfile.setId(5L);
        when(ngoProfileRepository.save(any(NgoProfile.class))).thenReturn(savedProfile);

        AuthUserResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals(20L, response.id());
        assertEquals(Role.NGO, response.role());
        verify(ngoProfileRepository).save(any(NgoProfile.class));
    }

    @Test
    void registerDuplicateEmailThrowsDuplicateResourceException() {
        RegisterRequest request = new RegisterRequest(
                "Hotel Sunrise",
                "kitchen@sunrise.example",
                "Str0ngPass!",
                Role.DONOR,
                null, null, null, null, null
        );

        when(userRepository.existsByEmail("kitchen@sunrise.example")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> authService.register(request));
    }

    @Test
    void registerAdminThrowsIllegalArgumentException() {
        RegisterRequest request = new RegisterRequest(
                "Admin",
                "admin@example.com",
                "Str0ngPass!",
                Role.ADMIN,
                null, null, null, null, null
        );

        assertThrows(IllegalArgumentException.class, () -> authService.register(request));
    }

    @Test
    void registerNgoWithoutAdvancedSettingsUsesDefaultsAndAllowsMissingLocation() {
        RegisterRequest request = new RegisterRequest(
                "Food Shelter",
                "shelter@ngo.example",
                "Str0ngPass!",
                Role.NGO,
                "9876543210", "Main Road", null, null, null
        );

        when(userRepository.existsByEmail("shelter@ngo.example")).thenReturn(false);
        when(passwordEncoder.encode("Str0ngPass!")).thenReturn("encodedPassword");
        User savedUser = new User("Food Shelter", "shelter@ngo.example", "encodedPassword", Role.NGO);
        savedUser.setId(21L);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(ngoProfileRepository.save(any(NgoProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AuthUserResponse response = authService.register(request);

        assertEquals(Role.NGO, response.role());
        ArgumentCaptor<NgoProfile> profileCaptor = ArgumentCaptor.forClass(NgoProfile.class);
        verify(ngoProfileRepository).save(profileCaptor.capture());
        assertEquals(50, profileCaptor.getValue().getCapacityMeals());
        assertEquals(10.0, profileCaptor.getValue().getServiceRadiusKm());
        assertEquals(EnumSet.allOf(FoodCategory.class), profileCaptor.getValue().getAcceptedCategoriesSet());
    }

    @Test
    void registerNgoRejectsOnlyOneCoordinate() {
        RegisterRequest request = new RegisterRequest(
                "Food Shelter",
                "shelter@ngo.example",
                "Str0ngPass!",
                Role.NGO,
                null, null, 15.84, null, null
        );
        when(userRepository.existsByEmail("shelter@ngo.example")).thenReturn(false);
        assertThrows(IllegalArgumentException.class, () -> authService.register(request));
    }

    @Test
    void loginSuccess() {
        LoginRequest request = new LoginRequest("kitchen@sunrise.example", "Str0ngPass!");

        User user = new User("Hotel Sunrise", "kitchen@sunrise.example", "hash", Role.DONOR);
        user.setId(10L);
        when(userRepository.findByEmail("kitchen@sunrise.example")).thenReturn(Optional.of(user));

        LoginResponse response = authService.login(request, httpRequest, httpResponse);

        assertNotNull(response);
        assertEquals(10L, response.id());
        assertEquals("Hotel Sunrise", response.name());
        assertEquals(Role.DONOR, response.role());
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(securityContextRepository).saveContext(any(), any(), any());
    }

    @Test
    void getMeSuccess() {
        User user = new User("Hotel Sunrise", "kitchen@sunrise.example", "hash", Role.DONOR);
        user.setId(10L);
        user.setLatitude(15.84);
        user.setLongitude(74.49);
        when(userRepository.findByEmail("kitchen@sunrise.example")).thenReturn(Optional.of(user));

        UserProfileResponse me = authService.getMe("kitchen@sunrise.example");

        assertNotNull(me);
        assertEquals(10L, me.id());
        assertEquals("Hotel Sunrise", me.name());
        assertEquals(15.84, me.latitude());
        assertEquals(74.49, me.longitude());
    }
}
