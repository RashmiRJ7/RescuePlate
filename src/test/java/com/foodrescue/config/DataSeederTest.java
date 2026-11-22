package com.foodrescue.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.foodrescue.entity.User;
import com.foodrescue.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class DataSeederTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private DataSeeder dataSeeder;

    @BeforeEach
    void setUp() {
        dataSeeder = new DataSeeder(userRepository, passwordEncoder);
        ReflectionTestUtils.setField(dataSeeder, "adminName", "System Admin");
        ReflectionTestUtils.setField(dataSeeder, "adminEmail", "admin@rescueplate.com");
        ReflectionTestUtils.setField(dataSeeder, "adminPassword", "Admin@12345");
    }

    @Test
    void seedsAdminWhenNotExists() {
        when(userRepository.existsByEmail("admin@rescueplate.com")).thenReturn(false);
        when(passwordEncoder.encode("Admin@12345")).thenReturn("encodedPassword");

        dataSeeder.run();

        verify(userRepository).save(any(User.class));
    }

    @Test
    void skipsAdminWhenAlreadyExists() {
        when(userRepository.existsByEmail("admin@rescueplate.com")).thenReturn(true);

        dataSeeder.run();

        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any(User.class));
    }
}
