package com.foodrescue.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.foodrescue.config.SecurityConfig;
import com.foodrescue.dto.AuthUserResponse;
import com.foodrescue.dto.LoginResponse;
import com.foodrescue.dto.RegisterRequest;
import com.foodrescue.dto.UserProfileResponse;
import com.foodrescue.enums.Role;
import com.foodrescue.exception.DuplicateResourceException;
import com.foodrescue.service.AuthService;
import com.foodrescue.service.CustomUserDetailsService;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void registerDonorSuccess() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenReturn(new AuthUserResponse(1L, "Hotel Sunrise", "kitchen@sunrise.example", Role.DONOR));

        String body = """
                {
                  "name": "Hotel Sunrise",
                  "email": "kitchen@sunrise.example",
                  "password": "Str0ngPass!",
                  "role": "DONOR",
                  "phone": "9876543210",
                  "address": "Club Road, Belagavi"
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Hotel Sunrise"))
                .andExpect(jsonPath("$.email").value("kitchen@sunrise.example"))
                .andExpect(jsonPath("$.role").value("DONOR"));
    }

    @Test
    void registerDuplicateEmailReturns409() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new DuplicateResourceException("A user with email kitchen@sunrise.example already exists."));

        String body = """
                {
                  "name": "Hotel Sunrise",
                  "email": "kitchen@sunrise.example",
                  "password": "Str0ngPass!",
                  "role": "DONOR"
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    void registerAdminRoleRejectedWith400() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new IllegalArgumentException("Registration with role ADMIN is not permitted."));

        String body = """
                {
                  "name": "Admin Fake",
                  "email": "adminfake@sunrise.example",
                  "password": "Str0ngPass!",
                  "role": "ADMIN"
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Registration with role ADMIN is not permitted."));
    }

    @Test
    void registerPasswordTooShortReturns400() throws Exception {
        String body = """
                {
                  "name": "Hotel Sunrise",
                  "email": "kitchen@sunrise.example",
                  "password": "short",
                  "role": "DONOR"
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void loginSuccessReturns200AndSetsUser() throws Exception {
        when(authService.login(any(), any(), any()))
                .thenReturn(new LoginResponse(1L, "Hotel Sunrise", Role.DONOR));

        String body = """
                {
                  "email": "kitchen@sunrise.example",
                  "password": "Str0ngPass!"
                }
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Hotel Sunrise"))
                .andExpect(jsonPath("$.role").value("DONOR"));
    }

    @Test
    void loginWrongCredentialsReturns401() throws Exception {
        doThrow(new BadCredentialsException("Bad credentials"))
                .when(authService).login(any(), any(), any());

        String body = """
                {
                  "email": "kitchen@sunrise.example",
                  "password": "WrongPassword"
                }
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void loginInactiveUserReturns401() throws Exception {
        doThrow(new DisabledException("User is disabled"))
                .when(authService).login(any(), any(), any());

        String body = """
                {
                  "email": "inactive@sunrise.example",
                  "password": "Str0ngPass!"
                }
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void logoutReturns200() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "donor@example.com", roles = {"DONOR"})
    void meAuthenticatedReturns200() throws Exception {
        when(authService.getMe("donor@example.com"))
                .thenReturn(new UserProfileResponse(1L, "Hotel Sunrise", "donor@example.com", Role.DONOR, 15.84, 74.49));

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Hotel Sunrise"))
                .andExpect(jsonPath("$.email").value("donor@example.com"))
                .andExpect(jsonPath("$.role").value("DONOR"))
                .andExpect(jsonPath("$.latitude").value(15.84))
                .andExpect(jsonPath("$.longitude").value(74.49));
    }

    @Test
    void meUnauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }
}
