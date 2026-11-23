package com.foodrescue.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.foodrescue.dto.NgoProfileResponse;
import com.foodrescue.dto.NgoProfileUpdateRequest;
import com.foodrescue.entity.NgoProfile;
import com.foodrescue.entity.User;
import com.foodrescue.exception.ResourceNotFoundException;
import com.foodrescue.repository.NgoProfileRepository;
import com.foodrescue.repository.UserRepository;

@Service
public class NgoProfileService {

    private final UserRepository userRepository;
    private final NgoProfileRepository ngoProfileRepository;

    public NgoProfileService(UserRepository userRepository, NgoProfileRepository ngoProfileRepository) {
        this.userRepository = userRepository;
        this.ngoProfileRepository = ngoProfileRepository;
    }

    @Transactional(readOnly = true)
    public NgoProfileResponse getProfile(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));

        NgoProfile profile = ngoProfileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("NGO profile not found for user: " + email));

        return NgoProfileResponse.from(profile);
    }

    @Transactional
    public NgoProfileResponse updateProfile(String email, NgoProfileUpdateRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));

        if ((request.latitude() == null) != (request.longitude() == null)) {
            throw new IllegalArgumentException("Latitude and longitude must be provided together.");
        }

        NgoProfile profile = ngoProfileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("NGO profile not found for user: " + email));

        if (request.latitude() != null) {
            user.setLatitude(request.latitude());
            user.setLongitude(request.longitude());
            userRepository.save(user);
        }
        profile.setCapacityMeals(request.capacityMeals());
        profile.setServiceRadiusKm(request.serviceRadiusKm());
        profile.setAcceptedCategoriesSet(request.acceptedCategories());

        NgoProfile saved = ngoProfileRepository.save(profile);
        return NgoProfileResponse.from(saved);
    }
}
