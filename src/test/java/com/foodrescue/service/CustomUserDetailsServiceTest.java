package com.foodrescue.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.foodrescue.entity.User;
import com.foodrescue.enums.Role;
import com.foodrescue.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    private CustomUserDetailsService userDetailsService;

    @BeforeEach
    void setUp() {
        userDetailsService = new CustomUserDetailsService(userRepository);
    }

    @Test
    void loadUserByUsernameSuccess() {
        User user = new User("Donor Joe", "donor@example.com", "$2a$10$hashedpw", Role.DONOR);
        user.setActive(true);

        when(userRepository.findByEmail("donor@example.com")).thenReturn(Optional.of(user));

        UserDetails userDetails = userDetailsService.loadUserByUsername("donor@example.com");

        assertNotNull(userDetails);
        assertEquals("donor@example.com", userDetails.getUsername());
        assertEquals("$2a$10$hashedpw", userDetails.getPassword());
        assertTrue(userDetails.isEnabled());
        assertTrue(userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_DONOR")));
    }

    @Test
    void loadUserByUsernameInactiveUserIsDisabled() {
        User user = new User("Inactive NGO", "ngo@example.com", "$2a$10$hashedpw", Role.NGO);
        user.setActive(false);

        when(userRepository.findByEmail("ngo@example.com")).thenReturn(Optional.of(user));

        UserDetails userDetails = userDetailsService.loadUserByUsername("ngo@example.com");

        assertNotNull(userDetails);
        assertFalse(userDetails.isEnabled()); // DisabledException will be thrown by Spring Security on auth
        assertTrue(userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_NGO")));
    }

    @Test
    void loadUserByUsernameNotFoundThrowsUsernameNotFoundException() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
                () -> userDetailsService.loadUserByUsername("missing@example.com"));
    }

    @Test
    void loadUserByUsernameEmptyThrowsUsernameNotFoundException() {
        assertThrows(UsernameNotFoundException.class,
                () -> userDetailsService.loadUserByUsername(""));
        assertThrows(UsernameNotFoundException.class,
                () -> userDetailsService.loadUserByUsername(null));
    }
}
