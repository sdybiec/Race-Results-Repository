package org.rowtown.rms.rrr.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.rowtown.rms.rrr.domain.UserRole;
import org.rowtown.rms.rrr.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * DEVELOPMENT-ONLY endpoint for minting JWT bearer tokens for local testing.
 *
 * <p>This bean is active only under the {@code dev} Spring profile
 * (run with {@code --spring.profiles.active=dev}). The application otherwise has
 * no token-issuing endpoint; production tokens are expected to be issued by an
 * external authentication service.</p>
 *
 * <p><b>Security:</b> this endpoint can forge an arbitrary identity, roles, and
 * regatta scope, so it must never be enabled in production. Two independent
 * guards keep it out of production: the {@code @Profile("dev")} annotation (the
 * bean does not exist under any other profile) and the security configuration,
 * which only permits this exact path unauthenticated.</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Profile("dev")
@Tag(name = "Dev Auth", description = "Development-only token generation (active under the 'dev' profile)")
public class DevAuthController {

    private final JwtTokenProvider tokenProvider;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    @PostMapping("/token")
    @Operation(
        summary = "Generate a JWT for local testing (dev only)",
        description = "Returns a signed JWT bearer token for the supplied identity and roles. "
            + "All fields are optional; an empty body yields an admin token (REGATTA_ADMIN, TIMER, "
            + "VIEWER) scoped to all regattas. Use the token as: Authorization: Bearer <token>."
    )
    public ResponseEntity<TokenResponse> generateToken(@RequestBody(required = false) TokenRequest request) {
        TokenRequest r = request != null ? request : new TokenRequest(null, null, null, null);

        String userId = r.userId() != null ? r.userId() : "dev-user";
        String username = r.username() != null ? r.username() : "dev";
        List<UserRole> roles = (r.roles() != null && !r.roles().isEmpty())
            ? r.roles()
            : List.of(UserRole.REGATTA_ADMIN, UserRole.TIMER, UserRole.VIEWER);
        List<String> regattaIds = (r.regattaIds() != null && !r.regattaIds().isEmpty())
            ? r.regattaIds()
            : List.of("*");

        String token = tokenProvider.generateToken(userId, username, roles, regattaIds);
        return ResponseEntity.ok(new TokenResponse(token, "Bearer", jwtExpiration, userId, username, roles, regattaIds));
    }

    /**
     * Token request; every field is optional and defaulted when omitted.
     */
    public record TokenRequest(
        String userId,
        String username,
        List<UserRole> roles,
        List<String> regattaIds
    ) {}

    /**
     * Generated token plus the effective claims and lifetime.
     */
    public record TokenResponse(
        String token,
        String tokenType,
        long expiresInMs,
        String userId,
        String username,
        List<UserRole> roles,
        List<String> regattaIds
    ) {}
}
