package org.rowtown.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter for rate limiting HTTP requests.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitingFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                   FilterChain filterChain) throws ServletException, IOException {
        String userId = extractUserId(request);
        String requestPath = request.getRequestURI();
        String method = request.getMethod();

        // Determine request type for rate limiting
        RateLimitType limitType = determineLimitType(requestPath, method);

        if (!rateLimitService.allowRequest(userId, limitType)) {
            long retryAfter = rateLimitService.getRetryAfter(userId, limitType);

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("X-RateLimit-Limit", String.valueOf(rateLimitService.getLimit(limitType)));
            response.setHeader("X-RateLimit-Remaining", "0");
            response.setHeader("Retry-After", String.valueOf(retryAfter));

            response.getWriter().write("{\"error\":\"Rate limit exceeded\",\"retryAfter\":" + retryAfter + "}");
            return;
        }

        // Add rate limit headers
        int limit = rateLimitService.getLimit(limitType);
        int remaining = rateLimitService.getRemaining(userId, limitType);
        response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));

        filterChain.doFilter(request, response);
    }

    /**
     * Extract user ID from request (from authentication context or IP address).
     */
    private String extractUserId(HttpServletRequest request) {
        // In a real implementation, this would extract from JWT token
        // For now, use IP address as fallback
        String userId = request.getHeader("X-User-Id");
        if (userId == null) {
            userId = request.getRemoteAddr();
        }
        return userId;
    }

    /**
     * Determine the type of rate limit to apply based on request path and method.
     */
    private RateLimitType determineLimitType(String path, String method) {
        if (path.contains("/search")) {
            return RateLimitType.SEARCH;
        } else if ("GET".equals(method)) {
            return RateLimitType.READ;
        } else {
            return RateLimitType.WRITE;
        }
    }
}
