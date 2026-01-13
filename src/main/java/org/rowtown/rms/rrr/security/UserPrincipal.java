package org.rowtown.rms.rrr.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.rowtown.rms.rrr.domain.UserRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * User principal for Spring Security authentication.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserPrincipal implements UserDetails {

    private String userId;
    private String username;
    private List<UserRole> roles;
    private List<String> regattaIds;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
            .collect(Collectors.toList());
    }

    @Override
    public String getPassword() {
        return null; // JWT-based authentication, no password needed
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    /**
     * Check if user has a specific role.
     */
    public boolean hasRole(UserRole role) {
        return roles != null && roles.contains(role);
    }

    /**
     * Check if user has access to a specific regatta.
     */
    public boolean hasAccessToRegatta(String regattaId) {
        return regattaIds != null && regattaIds.contains(regattaId);
    }
}
