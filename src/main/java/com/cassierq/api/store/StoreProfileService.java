package com.cassierq.api.store;

import com.cassierq.api.common.exception.BadRequestException;
import com.cassierq.api.common.exception.ResourceNotFoundException;
import com.cassierq.api.domain.entity.Store;
import com.cassierq.api.domain.entity.StoreSetting;
import com.cassierq.api.domain.repository.StoreRepository;
import com.cassierq.api.domain.repository.StoreSettingRepository;
import com.cassierq.api.security.AppUserPrincipal;
import com.cassierq.api.store.dto.StoreProfileResponse;
import com.cassierq.api.store.dto.UpdateStoreProfileRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StoreProfileService {

    private static final int MAX_KEY_LENGTH = 100;
    private static final int MAX_VALUE_LENGTH = 500;

    private final StoreRepository storeRepository;
    private final StoreSettingRepository storeSettingRepository;

    @Transactional(readOnly = true)
    public StoreProfileResponse get(AppUserPrincipal principal) {
        UUID storeId = requireStore(principal);
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Toko tidak ditemukan"));
        return StoreProfileResponse.from(store, settingsMap(storeId));
    }

    @Transactional
    public StoreProfileResponse update(AppUserPrincipal principal, UpdateStoreProfileRequest request) {
        UUID storeId = requireStore(principal);
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Toko tidak ditemukan"));

        if (request.storeName() != null) {
            store.setStoreName(request.storeName());
        }
        if (request.address() != null) {
            store.setAddress(request.address());
        }
        if (request.province() != null) {
            store.setProvince(request.province());
        }
        if (request.city() != null) {
            store.setCity(request.city());
        }
        if (request.phone() != null) {
            store.setPhone(request.phone());
        }
        storeRepository.save(store);

        if (request.settings() != null) {
            request.settings().forEach((key, value) -> upsertSetting(store, key, value));
        }

        return StoreProfileResponse.from(store, settingsMap(storeId));
    }

    private void upsertSetting(Store store, String key, String value) {
        if (key == null || key.isBlank()) {
            throw new BadRequestException("Kunci setting tidak boleh kosong");
        }
        if (key.length() > MAX_KEY_LENGTH) {
            throw new BadRequestException("Kunci setting '" + key + "' terlalu panjang (maks " + MAX_KEY_LENGTH + " karakter)");
        }
        if (value != null && value.length() > MAX_VALUE_LENGTH) {
            throw new BadRequestException("Nilai setting '" + key + "' terlalu panjang (maks " + MAX_VALUE_LENGTH + " karakter)");
        }

        StoreSetting setting = storeSettingRepository.findByStoreIdAndSettingKey(store.getId(), key)
                .orElseGet(() -> StoreSetting.builder().store(store).settingKey(key).build());
        setting.setSettingValue(value);
        storeSettingRepository.save(setting);
    }

    private Map<String, String> settingsMap(UUID storeId) {
        Map<String, String> map = new LinkedHashMap<>();
        storeSettingRepository.findByStoreId(storeId)
                .forEach(s -> map.put(s.getSettingKey(), s.getSettingValue()));
        return map;
    }

    private UUID requireStore(AppUserPrincipal principal) {
        UUID storeId = principal.getPrimaryStoreId();
        if (storeId == null) {
            throw new BadRequestException("Akun ini tidak terikat ke toko manapun");
        }
        return storeId;
    }
}
