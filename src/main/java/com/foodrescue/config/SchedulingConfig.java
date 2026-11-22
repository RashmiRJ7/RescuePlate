package com.foodrescue.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring's scheduled task execution for background jobs (Task S1, ARCHITECTURE.md §6).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
