package com.foodrescue.controller;

import java.security.Principal;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.foodrescue.dto.AuthUserResponse;
import com.foodrescue.dto.AdminSetupStatusResponse;
import com.foodrescue.dto.InitialAdminSetupRequest;
import com.foodrescue.dto.LoginRequest;
import com.foodrescue.dto.LoginResponse;
import com.foodrescue.dto.RegisterRequest;
import com.foodrescue.dto.UserProfileResponse;
import com.foodrescue.service.AuthService;
import com.foodrescue.service.AdminAccountService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AdminAccountService adminAccountService;

    public AuthController(AuthService authService, AdminAccountService adminAccountService) {
        this.authService = authService;
        this.adminAccountService = adminAccountService;
    }

    @GetMapping("/admin-setup/status")
    public ResponseEntity<AdminSetupStatusResponse> adminSetupStatus() {
        return ResponseEntity.ok(new AdminSetupStatusResponse(adminAccountService.isInitialSetupAvailable()));
    }

    @PostMapping("/admin-setup")
    public ResponseEntity<AuthUserResponse> createInitialAdmin(@Valid @RequestBody InitialAdminSetupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminAccountService.createInitialAdmin(request));
    }

    @PostMapping("/register")
    public ResponseEntity<AuthUserResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthUserResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletRequest httpRequest,
                                               HttpServletResponse httpResponse) {
        LoginResponse response = authService.login(request, httpRequest, httpResponse);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpServletRequest httpRequest,
                                                      HttpServletResponse httpResponse) {
        authService.logout(httpRequest, httpResponse);
        return ResponseEntity.ok(Map.of());
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getMe(Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw new BadCredentialsException("Not authenticated");
        }
        UserProfileResponse response = authService.getMe(principal.getName());
        return ResponseEntity.ok(response);
    }
}
