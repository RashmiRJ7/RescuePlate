package com.foodrescue.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.foodrescue.dto.AdminAccountRequest;
import com.foodrescue.dto.AuthUserResponse;
import com.foodrescue.dto.InitialAdminSetupRequest;
import com.foodrescue.entity.User;
import com.foodrescue.enums.Role;
import com.foodrescue.exception.DuplicateResourceException;
import com.foodrescue.exception.ValidationException;
import com.foodrescue.repository.UserRepository;

@Service
public class AdminAccountService {

    private static final Object INITIAL_SETUP_LOCK = new Object();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transactionTemplate;
    private final String setupKey;

    public AdminAccountService(UserRepository userRepository,
                               PasswordEncoder passwordEncoder,
                               PlatformTransactionManager transactionManager,
                               @Value("${app.admin.setup-key:}") String setupKey) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.setupKey = setupKey;
    }

    @Transactional(readOnly = true)
    public boolean isInitialSetupAvailable() {
        return setupKey != null && !setupKey.isBlank() && !userRepository.existsByRole(Role.ADMIN);
    }

    public AuthUserResponse createInitialAdmin(InitialAdminSetupRequest request) {
        synchronized (INITIAL_SETUP_LOCK) {
            return transactionTemplate.execute(status -> {
                if (setupKey == null || setupKey.isBlank()) {
                    throw new AccessDeniedException("Initial admin setup is not configured.");
                }

                byte[] expected = setupKey.getBytes(StandardCharsets.UTF_8);
                byte[] supplied = request.setupKey().getBytes(StandardCharsets.UTF_8);
                if (!MessageDigest.isEqual(expected, supplied)) {
                    throw new AccessDeniedException("Invalid initial admin setup key.");
                }
                if (userRepository.existsByRole(Role.ADMIN)) {
                    throw new DuplicateResourceException("Initial admin setup is already complete.");
                }
                if (!request.password().equals(request.confirmPassword())) {
                    throw new ValidationException("confirmPassword", "Passwords do not match.");
                }

                return saveAdmin(request.name(), request.email(), request.password(), request.phone());
            });
        }
    }

    @Transactional
    public AuthUserResponse createAdmin(AdminAccountRequest request) {
        return saveAdmin(request.name(), request.email(), request.password(), request.phone());
    }

    private AuthUserResponse saveAdmin(String name, String email, String rawPassword, String phone) {
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("A user with email " + email + " already exists.");
        }

        User admin = new User(name, email, passwordEncoder.encode(rawPassword), Role.ADMIN);
        admin.setPhone(phone);
        admin.setActive(true);
        User saved = userRepository.save(admin);
        return new AuthUserResponse(saved.getId(), saved.getName(), saved.getEmail(), saved.getRole());
    }
}
