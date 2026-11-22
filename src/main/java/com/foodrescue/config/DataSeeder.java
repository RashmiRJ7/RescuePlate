package com.foodrescue.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.foodrescue.entity.User;
import com.foodrescue.enums.Role;
import com.foodrescue.repository.UserRepository;

/**
 * Seeds the initial Admin account on application startup if it does not already exist.
 * Admin credentials are read from application properties per ARCHITECTURE.md §7.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.name:System Admin}")
    private String adminName;

    @Value("${app.admin.email:admin@rescueplate.com}")
    private String adminEmail;

    @Value("${app.admin.password:Admin@12345}")
    private String adminPassword;

    public DataSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (!userRepository.existsByEmail(adminEmail)) {
            User admin = new User(adminName, adminEmail, passwordEncoder.encode(adminPassword), Role.ADMIN);
            admin.setActive(true);
            userRepository.save(admin);
            log.info("Seeded initial admin account with email: {}", adminEmail);
        } else {
            log.info("Admin account already exists: {}", adminEmail);
        }
    }
}
