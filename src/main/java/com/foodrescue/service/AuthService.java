package com.foodrescue.service;

import java.util.EnumSet;
import java.util.stream.Collectors;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import com.foodrescue.exception.ResourceNotFoundException;
import com.foodrescue.repository.NgoProfileRepository;
import com.foodrescue.repository.UserRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final NgoProfileRepository ngoProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;

    public AuthService(UserRepository userRepository,
                       NgoProfileRepository ngoProfileRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       SecurityContextRepository securityContextRepository) {
        this.userRepository = userRepository;
        this.ngoProfileRepository = ngoProfileRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
    }

    @Transactional
    public AuthUserResponse register(RegisterRequest request) {
        if (request.role() == Role.ADMIN) {
            throw new IllegalArgumentException("Registration with role ADMIN is not permitted.");
        }

        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("A user with email " + request.email() + " already exists.");
        }

        if ((request.latitude() == null) != (request.longitude() == null)) {
            throw new IllegalArgumentException("Latitude and longitude must be provided together.");
        }

        User user = new User(
                request.name(),
                request.email(),
                passwordEncoder.encode(request.password()),
                request.role()
        );
        user.setPhone(request.phone());
        user.setAddress(request.address());
        user.setLatitude(request.latitude());
        user.setLongitude(request.longitude());
        user.setActive(true);

        User savedUser = userRepository.save(user);

        if (request.role() == Role.NGO) {
            var ngoSettings = request.ngoProfile() != null
                    ? request.ngoProfile()
                    : new NgoProfileRegisterRequest(
                            50,
                            10.0,
                            EnumSet.allOf(FoodCategory.class)
                    );
            String categories = ngoSettings.acceptedCategories().stream()
                    .map(FoodCategory::name)
                    .collect(Collectors.joining(","));

            NgoProfile profile = new NgoProfile(
                    savedUser,
                    ngoSettings.capacityMeals(),
                    ngoSettings.serviceRadiusKm(),
                    categories
            );
            NgoProfile savedProfile = ngoProfileRepository.save(profile);
            savedUser.setNgoProfile(savedProfile);
        }

        return new AuthUserResponse(
                savedUser.getId(),
                savedUser.getName(),
                savedUser.getEmail(),
                savedUser.getRole()
        );
    }

    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + request.email()));

        return new LoginResponse(user.getId(), user.getName(), user.getRole());
    }

    public void logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        new SecurityContextLogoutHandler().logout(httpRequest, httpResponse, auth);
        SecurityContextHolder.clearContext();
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getMe(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));

        return new UserProfileResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getLatitude(),
                user.getLongitude()
        );
    }
}
