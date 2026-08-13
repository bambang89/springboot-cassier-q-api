package com.cassierq.api.security;

import com.cassierq.api.domain.entity.User;
import com.cassierq.api.domain.entity.UserRole;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

@Getter
public class AppUserPrincipal implements UserDetails {

    private final UUID userId;
    private final UUID storeId;
    private final String email;
    private final String passwordHash;
    private final UserRole role;
    private final boolean active;

    public AppUserPrincipal(User user) {
        this.userId = user.getId();
        this.storeId = user.getStore().getId();
        this.email = user.getEmail();
        this.passwordHash = user.getPasswordHash();
        this.role = user.getRole();
        this.active = user.isActive();
    }

    public AppUserPrincipal(UUID userId, UUID storeId, String email, UserRole role) {
        this.userId = userId;
        this.storeId = storeId;
        this.email = email;
        this.passwordHash = null;
        this.role = role;
        this.active = true;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
