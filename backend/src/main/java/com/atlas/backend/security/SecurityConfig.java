package com.atlas.backend.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Spring Security configuration for Atlas — stateless JWT API.
 *
 * <p>Policy:
 * <ul>
 *   <li>CSRF disabled — irrelevant for a stateless, token-based API.</li>
 *   <li>Sessions disabled ({@code STATELESS}) — JWTs are self-contained.</li>
 *   <li>Public routes: {@code POST /api/auth/register},
 *       {@code POST /api/auth/login}, {@code GET /api/health}.</li>
 *   <li>All other {@code /api/**} routes require authentication.</li>
 *   <li>Unauthenticated requests receive a JSON 401 — never an HTML
 *       redirect — matching the Atlas error shape
 *       ({@code error_code, message}).</li>
 * </ul>
 *
 * <p>Per 15_SECURITY_AND_PRIVACY.md §1–2.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm ->
                sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, "/api/auth/register").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                .requestMatchers(HttpMethod.GET,  "/api/health").permitAll()
                .anyRequest().authenticated())
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(this::handleAuthenticationFailure))
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Returns a JSON 401 matching the Atlas error shape
     * ({@code {"error_code":"UNAUTHORIZED","message":"..."}}).
     * Never returns an HTML redirect or Spring's default login page.
     */
    private void handleAuthenticationFailure(HttpServletRequest  request,
                                             HttpServletResponse response,
                                             org.springframework.security.core.AuthenticationException ex)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error_code\":\"UNAUTHORIZED\",\"message\":\"Authentication required\"}");
    }

    /**
     * BCrypt password encoder bean.
     * Used by AuthService to hash and verify passwords.
     * Algorithm selected per DEC-0004 (docs/agent/DECISION_LOG.md).
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
