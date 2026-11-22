package com.foodrescue.controller;

import java.security.Principal;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.foodrescue.dto.NgoProfileResponse;
import com.foodrescue.dto.NgoProfileUpdateRequest;
import com.foodrescue.service.NgoProfileService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/ngo/profile")
public class NgoProfileController {

    private final NgoProfileService ngoProfileService;

    public NgoProfileController(NgoProfileService ngoProfileService) {
        this.ngoProfileService = ngoProfileService;
    }

    @GetMapping
    public ResponseEntity<NgoProfileResponse> getProfile(Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw new BadCredentialsException("Not authenticated");
        }
        NgoProfileResponse response = ngoProfileService.getProfile(principal.getName());
        return ResponseEntity.ok(response);
    }

    @PutMapping
    public ResponseEntity<NgoProfileResponse> updateProfile(Principal principal,
                                                           @Valid @RequestBody NgoProfileUpdateRequest request) {
        if (principal == null || principal.getName() == null) {
            throw new BadCredentialsException("Not authenticated");
        }
        NgoProfileResponse response = ngoProfileService.updateProfile(principal.getName(), request);
        return ResponseEntity.ok(response);
    }
}
