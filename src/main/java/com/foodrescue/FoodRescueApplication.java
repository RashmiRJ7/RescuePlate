package com.foodrescue;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

/**
 * Entry point for the RescuePlate layered monolith (REST API + static frontend).
 *
 * <p>The default in-memory user is excluded so no generated password is printed.
 * Real users are loaded from the database in task F4 (security).
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class FoodRescueApplication {

    public static void main(String[] args) {
        SpringApplication.run(FoodRescueApplication.class, args);
    }
}
