package com.cassierq.api.security;

import com.cassierq.api.domain.entity.User;
import com.cassierq.api.domain.entity.UserRole;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

@Getter
public class AppUserPrincipal implements UserDetails {

    private final UUID userId;
    private final UUID employeeId;
    private final String username;
    private final String email;
    private final String passwordHash;
    private final boolean superadmin;
    private final boolean active;
    private final List<RoleGrant> roleGrants;
    private final String jti;

    public AppUserPrincipal(User user, List<RoleGrant> roleGrants) {
        this.userId = user.getId();
        this.employeeId = user.getEmployee().getId();
        this.username = user.getUsername();
        this.email = user.getEmail();
        this.passwordHash = user.getPasswordHash();
        this.superadmin = user.isSuperadmin();
        this.active = user.isActive();
        this.roleGrants = roleGrants;
        this.jti = null;
    }

    public AppUserPrincipal(UUID userId, UUID employeeId, String username, String email, boolean superadmin, boolean active,
            List<RoleGrant> roleGrants, String jti) {
        this.userId = userId;
        this.employeeId = employeeId;
        this.username = username;
        this.email = email;
        this.passwordHash = null;
        this.superadmin = superadmin;
        this.active = active;
        this.roleGrants = roleGrants;
        this.jti = jti;
    }

    /** Builds a principal from a loaded {@link User} plus their {@code user_roles} rows (role + store already fetched). */
    public static AppUserPrincipal of(User user, List<UserRole> userRoles) {
        List<RoleGrant> grants = userRoles.stream()
                .map(ur -> new RoleGrant(
                        ur.getRole().getRoleCode(),
                        ur.getStore() != null ? ur.getStore().getId() : null))
                .toList();
        return new AppUserPrincipal(user, grants);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        if (superadmin) {
            authorities.add(new SimpleGrantedAuthority("ROLE_SUPERADMIN"));
        }
        roleGrants.forEach(grant -> authorities.add(new SimpleGrantedAuthority("ROLE_" + grant.roleCode())));
        return authorities;
    }
    public boolean hasRole(String roleCode) {
        return getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + roleCode));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }

    public UUID getPrimaryStoreId() {
        return roleGrants.stream()
                .map(RoleGrant::storeId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }
}
