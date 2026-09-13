package com.atlas.backend.security;

import com.atlas.backend.user.User;
import com.atlas.backend.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Extracts and validates the JWT from the {@code Authorization: Bearer …}
 * header on each request, then populates the {@link SecurityContextHolder}.
 *
 * <p>If the token is missing or invalid the filter clears the security context
 * and allows the request to continue — downstream Spring Security configuration
 * will then return 401 for any protected route. This keeps the filter
 * responsibility narrow: <em>authenticate when possible, never block on its own</em>.
 *
 * <p>Security invariants:
 * <ul>
 *   <li>User identity is obtained from the validated JWT subject — never from
 *       the request body or a query parameter.</li>
 *   <li>Token values are never logged (see JwtService).</li>
 * </ul>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService     jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   UserRepository userRepository) {
        this.jwtService     = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest  request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain         chain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            // No bearer token — pass through; security config handles 401.
            chain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        if (!jwtService.validateToken(token)) {
            // Invalid token — clear context; security config handles 401.
            SecurityContextHolder.clearContext();
            chain.doFilter(request, response);
            return;
        }

        // Only reach here with a structurally valid, correctly signed,
        // non-expired token. Fetch the user to ensure they still exist.
        Long userId = jwtService.getUserIdFromToken(token);
        Optional<User> userOpt = userRepository.findById(userId);

        if (userOpt.isEmpty()) {
            log.warn("JWT references user id {} which no longer exists", userId);
            SecurityContextHolder.clearContext();
            chain.doFilter(request, response);
            return;
        }

        User user = userOpt.get();
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        user,
                        null,
                        user.getAuthorities());
        authentication.setDetails(
                new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        chain.doFilter(request, response);
    }
}
