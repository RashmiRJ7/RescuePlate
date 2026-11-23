package com.foodrescue.config;

import java.nio.charset.StandardCharsets;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodrescue.dto.ErrorResponse;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Spring Security configuration per ARCHITECTURE.md §7 and BACKEND_ROADMAP.md Step 5.
 *
 * <p>Enforces:
 * <ul>
 *   <li>BCrypt password hashing</li>
 *   <li>Session-based authentication with JSESSIONID</li>
 *   <li>Role-based access control with specific rules ordered before general ones</li>
 *   <li>Standard JSON 401 (Unauthorized) and 403 (Forbidden) error responses</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final ObjectMapper objectMapper;

    public SecurityConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .securityContext(context -> context
                .securityContextRepository(securityContextRepository())
            )
            .authorizeHttpRequests(auth -> auth
                // 1. Public paths and static assets
                .requestMatchers(
                        "/",
                        "/index.html",
                        "/login.html",
                        "/register.html",
                        "/admin-setup.html",
                        "/favicon.ico",
                        "/error",
                        "/css/**",
                        "/js/**",
                        "/assets/**",
                        "/vendor/**"
                ).permitAll()
                .requestMatchers("/api/auth/me").authenticated()
                .requestMatchers("/api/auth/**", "/api/health").permitAll()

                // 2. Specific role rules (more specific rules declared first per ARCHITECTURE.md §7)
                // Claim cancel: NGO in Phase 1 (ADMIN added in Phase 2)
                .requestMatchers(HttpMethod.PATCH, "/api/claims/*/cancel").hasRole("NGO")

                // Donation cancel (Phase 2 endpoint)
                .requestMatchers(HttpMethod.PATCH, "/api/donations/*/cancel").hasAnyRole("DONOR", "ADMIN")

                // NGO-only paths
                .requestMatchers("/api/donations/feed").hasRole("NGO")
                .requestMatchers("/api/donations/*/claim").hasRole("NGO")
                .requestMatchers("/api/claims/**").hasRole("NGO")
                .requestMatchers("/api/ngo/**").hasRole("NGO")

                // Donor-only paths
                .requestMatchers(HttpMethod.POST, "/api/donations").hasRole("DONOR")
                .requestMatchers("/api/donations/mine").hasRole("DONOR")

                // Admin-only paths
                .requestMatchers("/api/admin/**").hasRole("ADMIN")

                // Other /api/** paths require any authenticated user
                .requestMatchers("/api/**").authenticated()

                // Any other request requires authentication
                .anyRequest().authenticated()
            )
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    ErrorResponse body = ErrorResponse.of(
                            HttpStatus.UNAUTHORIZED.value(),
                            HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                            authException.getMessage() != null && !authException.getMessage().isBlank()
                                    ? authException.getMessage()
                                    : "Full authentication is required to access this resource",
                            request.getRequestURI()
                    );
                    objectMapper.writeValue(response.getOutputStream(), body);
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    ErrorResponse body = ErrorResponse.of(
                            HttpStatus.FORBIDDEN.value(),
                            HttpStatus.FORBIDDEN.getReasonPhrase(),
                            "Access denied.",
                            request.getRequestURI()
                    );
                    objectMapper.writeValue(response.getOutputStream(), body);
                })
            );

        return http.build();
    }
}
