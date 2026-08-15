package com.cassierq.api.domain.repository;

import com.cassierq.api.domain.entity.UserRole;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface UserRoleRepository extends JpaRepository<UserRole, UUID> {

    // Fetch-joins role (+ store, when scoped) in one round trip — this runs
    // on every login/token issuance, so avoiding N+1 here matters.
    @Query("""
            select ur from UserRole ur
            join fetch ur.role
            left join fetch ur.store
            where ur.user.id = :userId
            """)
    List<UserRole> findAllByUserIdFetchingRole(UUID userId);

    // Used by EmployeeService.update() to swap someone's role grant at their
    // own store — the composite unique key is (user_id, role_id, store_id),
    // so a plain re-save can't just change the role on the existing row
    // without risking a duplicate; delete-then-insert is simpler.
    @Modifying
    @Query("delete from UserRole ur where ur.user.id = :userId and ur.store.id = :storeId")
    void deleteByUserIdAndStoreId(UUID userId, UUID storeId);
}
