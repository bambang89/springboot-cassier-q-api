package com.cassierq.api.access;

import com.cassierq.api.domain.entity.StoreSetting;
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
 * Per-role action permissions inside each menu ("sub menu" in Hak Akses Menu), e.g.
 * {@code products.create}. Stored per store in store_settings as
 * {@code ACTION_ACCESS_<ROLE>} (comma separated). SUPERADMIN and KEPALA_TOKO can do everything.
 *
 * <p>Used by endpoints as {@code @PreAuthorize("@actionAccess.allowed(principal, 'products.create')")}.
 * Defaults equal the role rules the API had before this setting existed.
 */
@Service("actionAccess")
@RequiredArgsConstructor
public class ActionAccessService {

    /** Menu → its actions (code → label), in display order. Menus without actions are view-only. */
    public static final Map<String, Map<String, String>> ACTIONS = new LinkedHashMap<>();

    static {
        ACTIONS.put("pos", actions(
                "create", "Catat penjualan",
                "discount", "Beri diskon manual",
                "promo", "Pakai kode promo"));
        ACTIONS.put("orders", actions("void", "Batalkan (void) transaksi"));
        ACTIONS.put("products", actions(
                "create", "Tambah",
                "update", "Ubah",
                "delete", "Hapus",
                "price_tiers", "Atur harga grosir"));
        ACTIONS.put("categories", actions("create", "Tambah", "update", "Ubah", "delete", "Hapus"));
        ACTIONS.put("units", actions("create", "Tambah", "update", "Ubah", "delete", "Hapus"));
        ACTIONS.put("inventory", actions(
                "restock", "Restock / tambah stok",
                "opname", "Stok opname",
                "bad_stock", "Catat bad stock",
                "bad_stock_resolve", "Retur / musnahkan bad stock"));
        ACTIONS.put("customers", actions("create", "Tambah", "update", "Ubah", "payment", "Catat pembayaran utang"));
        ACTIONS.put("suppliers", actions("create", "Tambah", "update", "Ubah", "delete", "Hapus"));
        ACTIONS.put("purchase_orders", actions("create", "Buat PO", "receive", "Terima barang", "cancel", "Batalkan PO"));
    }

    /** Same permissions each staff role had before actions became configurable. */
    static final Map<String, Set<String>> DEFAULTS = Map.of(
            "PRODUCT", Set.of(
                    "products.create", "products.update", "products.delete", "products.price_tiers",
                    "categories.create", "categories.update", "categories.delete",
                    "units.create", "units.update", "units.delete"),
            "GUDANG", Set.of(
                    "inventory.restock", "inventory.opname", "inventory.bad_stock", "inventory.bad_stock_resolve",
                    "suppliers.create", "suppliers.update", "suppliers.delete",
                    "purchase_orders.create", "purchase_orders.receive", "purchase_orders.cancel"),
            "KASIR", Set.of("pos.create", "pos.discount", "pos.promo", "customers.create", "customers.payment"));

    /**
     * Actions added after a store may already have saved its settings, with the action
     * that implied them before. A list saved before {@link #CATALOG_VERSION} gets them
     * automatically, so nobody silently loses an ability they had.
     */
    static final Map<String, Implied> IMPLIED_BY = Map.of(
            "pos.discount", new Implied("pos.create", 2),
            "pos.promo", new Implied("pos.create", 2),
            "products.price_tiers", new Implied("products.update", 2),
            "inventory.opname", new Implied("inventory.restock", 3),
            "inventory.bad_stock", new Implied("inventory.restock", 3),
            "inventory.bad_stock_resolve", new Implied("inventory.restock", 3));

    /** An action added in catalog version {@code since}, granted to lists that already had {@code by}. */
    record Implied(String by, int since) {
    }

    /** Current catalog version, stored as "v3" with the action list (no marker = version 1). */
    static final int VERSION = 3;
    static final String CATALOG_VERSION = "v" + VERSION;

    static final String KEY_PREFIX = "ACTION_ACCESS_";

    private final StoreSettingRepository storeSettingRepository;

    /** True when the caller may perform {@code action} ("menu.action") in their active store. */
    @Transactional(readOnly = true)
    public boolean allowed(AppUserPrincipal principal, String action) {
        if (principal == null) return false;
        if (RolePolicy.isManager(principal)) return true;
        return staffActions(principal).contains(action);
    }

    /** True when the caller may perform at least one action of {@code menu} (used for whole-menu endpoints). */
    @Transactional(readOnly = true)
    public boolean allowedAny(AppUserPrincipal principal, String menu) {
        if (principal == null) return false;
        if (RolePolicy.isManager(principal)) return true;
        return staffActions(principal).stream().anyMatch(a -> a.startsWith(menu + "."));
    }

    /** Union of the actions granted to the caller's staff roles in their active store. */
    public Set<String> staffActions(AppUserPrincipal principal) {
        UUID storeId = principal.getPrimaryStoreId();
        Set<String> actions = new LinkedHashSet<>();
        if (storeId == null) return actions;
        for (RoleGrant grant : principal.getRoleGrants()) {
            if (RolePolicy.STAFF_ROLES.contains(grant.roleCode())
                    && (grant.storeId() == null || grant.storeId().equals(storeId))) {
                actions.addAll(actionsFor(storeId, grant.roleCode()));
            }
        }
        return actions;
    }

    /** Actions configured for a staff role in a store (defaults when never saved). */
    public List<String> actionsFor(UUID storeId, String role) {
        return storeSettingRepository.findByStoreIdAndSettingKey(storeId, KEY_PREFIX + role)
                .map(StoreSetting::getSettingValue)
                .map(ActionAccessService::parseStored)
                .orElseGet(() -> ordered(DEFAULTS.getOrDefault(role, Set.of())));
    }

    /** Parses a stored list, adding actions introduced later when their older equivalent is granted. */
    static List<String> parseStored(String value) {
        if (value == null || value.isBlank()) return List.of();
        List<String> parts = Arrays.stream(value.split(",")).map(String::trim).toList();
        Set<String> actions = new LinkedHashSet<>(parts.stream().filter(ActionAccessService::isKnown).toList());
        int savedVersion = parts.stream()
                .filter(p -> p.matches("v\\d+"))
                .mapToInt(p -> Integer.parseInt(p.substring(1)))
                .max()
                .orElse(1);
        IMPLIED_BY.forEach((added, implied) -> {
            if (implied.since() > savedVersion && actions.contains(implied.by())) actions.add(added);
        });
        return ordered(actions);
    }

    /** Value to store for a granted action list (catalog order + version marker). */
    static String toStored(Set<String> actions) {
        List<String> list = new java.util.ArrayList<>(ordered(actions));
        list.add(CATALOG_VERSION);
        return String.join(",", list);
    }

    /** Every known action code in catalog order. */
    public static List<String> allActions() {
        return ACTIONS.entrySet().stream()
                .flatMap(e -> e.getValue().keySet().stream().map(code -> e.getKey() + "." + code))
                .toList();
    }

    static boolean isKnown(String action) {
        int dot = action.indexOf('.');
        return dot > 0 && ACTIONS.getOrDefault(action.substring(0, dot), Map.of()).containsKey(action.substring(dot + 1));
    }

    /** Sorts actions in catalog order. */
    static List<String> ordered(Set<String> actions) {
        return allActions().stream().filter(actions::contains).toList();
    }

    private static Map<String, String> actions(String... codeLabelPairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < codeLabelPairs.length; i += 2) map.put(codeLabelPairs[i], codeLabelPairs[i + 1]);
        return map;
    }
}
