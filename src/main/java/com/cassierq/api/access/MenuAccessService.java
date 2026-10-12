package com.cassierq.api.access;

import com.cassierq.api.access.dto.MenuAccessResponse;
import com.cassierq.api.access.dto.MyMenuAccessResponse;
import com.cassierq.api.common.exception.BadRequestException;
import com.cassierq.api.domain.entity.StoreSetting;
import com.cassierq.api.domain.repository.StoreRepository;
import com.cassierq.api.domain.repository.StoreSettingRepository;
import com.cassierq.api.security.AppUserPrincipal;
import com.cassierq.api.security.RoleGrant;
import com.cassierq.api.security.RolePolicy;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hak tampilan menu per role, diatur KEPALA_TOKO untuk tokonya. Disimpan di
 * store_settings dengan key {@code MENU_ACCESS_<ROLE>} berisi daftar menu
 * dipisah koma. SUPERADMIN & KEPALA_TOKO selalu melihat semua menu.
 *
 * <p>Ini membatasi <em>tampilan</em> app; hak akses data tetap dijaga oleh
 * role di tiap endpoint (@PreAuthorize).
 */
@Service
@RequiredArgsConstructor
public class MenuAccessService {

    /** Semua menu yang bisa diatur, urut seperti di app. "dashboard" selalu tampil. */
    public static final List<String> MENUS = List.of(
            "dashboard", "pos", "orders", "pesanan", "products", "categories", "units", "inventory",
            "customers", "expenses", "reports", "suppliers", "purchase_orders", "employees", "outlet",
            "integrations", "printer");

    private static final String ALWAYS_VISIBLE = "dashboard";
    private static final String KEY_PREFIX = "MENU_ACCESS_";

    private static final Map<String, List<String>> DEFAULTS = Map.of(
            "PRODUCT", List.of("dashboard", "products", "categories", "units", "inventory"),
            "GUDANG", List.of("dashboard", "products", "categories", "units", "inventory", "suppliers", "purchase_orders"),
            "KASIR", List.of("dashboard", "pos", "orders", "pesanan", "customers", "printer"));

    private final StoreSettingRepository storeSettingRepository;
    private final StoreRepository storeRepository;

    /** Matriks lengkap role → menu untuk layar pengaturan KEPALA_TOKO. */
    @Transactional(readOnly = true)
    public MenuAccessResponse get(AppUserPrincipal principal) {
        UUID storeId = requireStore(principal);
        Map<String, List<String>> roles = new LinkedHashMap<>();
        RolePolicy.STAFF_ROLES.forEach(role -> roles.put(role, menusFor(storeId, role)));
        return new MenuAccessResponse(MENUS, roles);
    }

    @Transactional
    public MenuAccessResponse update(AppUserPrincipal principal, String roleCode, List<String> menus) {
        UUID storeId = requireStore(principal);
        String role = roleCode.trim().toUpperCase();
        if (!RolePolicy.STAFF_ROLES.contains(role)) {
            throw new BadRequestException("Tampilan hanya bisa diatur untuk role " + String.join(", ", RolePolicy.STAFF_ROLES));
        }
        Set<String> cleaned = new LinkedHashSet<>();
        cleaned.add(ALWAYS_VISIBLE);
        for (String menu : menus) {
            if (!MENUS.contains(menu)) {
                throw new BadRequestException("Menu tidak dikenal: " + menu);
            }
            cleaned.add(menu);
        }
        // Simpan dalam urutan katalog supaya tampilan app konsisten.
        String value = String.join(",", MENUS.stream().filter(cleaned::contains).toList());

        String key = KEY_PREFIX + role;
        StoreSetting setting = storeSettingRepository.findByStoreIdAndSettingKey(storeId, key)
                .orElseGet(() -> StoreSetting.builder().store(storeRepository.getReferenceById(storeId)).settingKey(key).build());
        setting.setSettingValue(value);
        storeSettingRepository.save(setting);
        return get(principal);
    }

    /** Menu yang boleh dilihat user yang sedang login. */
    @Transactional(readOnly = true)
    public MyMenuAccessResponse mine(AppUserPrincipal principal) {
        if (RolePolicy.isManager(principal)) {
            return new MyMenuAccessResponse(false, MENUS);
        }
        UUID storeId = principal.getPrimaryStoreId();
        Set<String> allowed = new LinkedHashSet<>();
        for (RoleGrant grant : principal.getRoleGrants()) {
            if (storeId != null && RolePolicy.STAFF_ROLES.contains(grant.roleCode())
                    && (grant.storeId() == null || grant.storeId().equals(storeId))) {
                allowed.addAll(menusFor(storeId, grant.roleCode()));
            }
        }
        allowed.add(ALWAYS_VISIBLE);
        return new MyMenuAccessResponse(true, MENUS.stream().filter(allowed::contains).toList());
    }

    private List<String> menusFor(UUID storeId, String role) {
        return storeSettingRepository.findByStoreIdAndSettingKey(storeId, KEY_PREFIX + role)
                .map(StoreSetting::getSettingValue)
                .filter(value -> value != null && !value.isBlank())
                .map(value -> Arrays.stream(value.split(",")).map(String::trim).filter(MENUS::contains).toList())
                .orElse(DEFAULTS.getOrDefault(role, List.of(ALWAYS_VISIBLE)));
    }

    private UUID requireStore(AppUserPrincipal principal) {
        UUID storeId = principal.getPrimaryStoreId();
        if (storeId == null) {
            throw new BadRequestException("Pilih toko terlebih dahulu");
        }
        return storeId;
    }
}
