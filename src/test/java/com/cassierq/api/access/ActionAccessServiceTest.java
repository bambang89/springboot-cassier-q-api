package com.cassierq.api.access;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ActionAccessServiceTest {

    @Test
    void defaultsMatchThePreviousRoleRules() {
        assertThat(ActionAccessService.DEFAULTS.get("KASIR")).containsExactlyInAnyOrder("pos.create", "pos.discount", "pos.promo", "customers.create", "customers.payment");
        assertThat(ActionAccessService.DEFAULTS.get("PRODUCT")).contains("products.create", "products.update", "products.delete");
        assertThat(ActionAccessService.DEFAULTS.get("GUDANG")).contains("inventory.restock", "purchase_orders.receive");
        // Every default is a known action.
        ActionAccessService.DEFAULTS.values().forEach(actions -> actions.forEach(a -> assertThat(ActionAccessService.isKnown(a)).as(a).isTrue()));
    }

    @Test
    void unknownActionsAreRejectedAndOrderFollowsTheCatalog() {
        assertThat(ActionAccessService.isKnown("products.create")).isTrue();
        assertThat(ActionAccessService.isKnown("products.fly")).isFalse();
        assertThat(ActionAccessService.isKnown("dashboard.create")).isFalse();
        assertThat(ActionAccessService.ordered(Set.of("products.delete", "pos.create", "products.create")))
                .isEqualTo(List.of("pos.create", "products.create", "products.delete"));
    }

    @Test
    void listsSavedBeforeTheNewActionsKeepTheirAbilities() {
        // Saved by an older version (no marker): pos.create implied discount + promo.
        assertThat(ActionAccessService.parseStored("pos.create,customers.create"))
                .containsExactly("pos.create", "pos.discount", "pos.promo", "customers.create");
        // Saved by this version: exactly what the store head chose.
        assertThat(ActionAccessService.parseStored("pos.create,customers.create,v2"))
                .containsExactly("pos.create", "customers.create");
        assertThat(ActionAccessService.toStored(Set.of("products.update", "pos.create")))
                .isEqualTo("pos.create,products.update,v3");
        // Saved as v2 by a store head who allowed restock: the inventory actions added in v3 follow it.
        assertThat(ActionAccessService.parseStored("inventory.restock,v2"))
                .containsExactly("inventory.restock", "inventory.opname", "inventory.bad_stock", "inventory.bad_stock_resolve");
    }
}
