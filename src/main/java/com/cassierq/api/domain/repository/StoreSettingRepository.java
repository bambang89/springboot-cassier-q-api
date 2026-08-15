package com.cassierq.api.domain.repository;

import com.cassierq.api.domain.entity.StoreSetting;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreSettingRepository extends JpaRepository<StoreSetting, UUID> {

    List<StoreSetting> findByStoreId(UUID storeId);

    Optional<StoreSetting> findByStoreIdAndSettingKey(UUID storeId, String settingKey);
}
