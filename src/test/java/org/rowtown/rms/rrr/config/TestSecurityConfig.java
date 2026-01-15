package org.rowtown.rms.rrr.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.rowtown.rms.rrr.domain.UserRole;
import org.rowtown.rms.rrr.security.UserPrincipal;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Test security configuration that disables authentication for integration tests
 * and provides a default test user with admin privileges.
 */
@TestConfiguration
public class TestSecurityConfig {

    @Bean
    @Primary
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll()
            )
            .addFilterBefore(testUserFilter(), org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Filter that sets up a test user in the SecurityContext for each request.
     */
    @Bean
    public OncePerRequestFilter testUserFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                          FilterChain filterChain) throws ServletException, IOException {
                // Create a test user with admin privileges and access to all regattas
                UserPrincipal testUser = new UserPrincipal(
                    "test-user-id",
                    "test@example.com",
                    List.of(UserRole.REGATTA_ADMIN, UserRole.TIMER, UserRole.VIEWER),
                    List.of("*") // Access to all regattas
                );

                UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities());

                SecurityContextHolder.getContext().setAuthentication(authentication);

                try {
                    filterChain.doFilter(request, response);
                } finally {
                    SecurityContextHolder.clearContext();
                }
            }
        };
    }
}
