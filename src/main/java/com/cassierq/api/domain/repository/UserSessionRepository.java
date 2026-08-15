package com.cassierq.api.domain.repository;

import com.cassierq.api.domain.entity.UserSession;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    Optional<UserSession> findByJti(String jti);
    
    @Modifying
    @Query("update UserSession s set s.revoked = true where s.user.id = :userId and s.revoked = false")
    void revokeAllByUserId(UUID userId);
}
