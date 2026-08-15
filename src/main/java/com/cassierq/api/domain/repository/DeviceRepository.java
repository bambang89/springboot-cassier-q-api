package com.cassierq.api.domain.repository;

import com.cassierq.api.domain.entity.Device;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface DeviceRepository extends JpaRepository<Device, UUID> {

    Optional<Device> findByUserIdAndDeviceId(UUID userId, String deviceId);

    Optional<Device> findByUserIdAndDeviceType(UUID userId, String deviceType);

    List<Device> findByUserId(UUID userId);

    @Modifying
    @Query("delete from Device d where d.user.id = :userId")
    void deleteAllByUserId(UUID userId);
}
