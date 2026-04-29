package com.authservice.api.dto;

import com.authservice.domain.items.ItemCatalogEntry;
import com.authservice.domain.items.ItemConsumption;
import com.authservice.domain.items.UserItem;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * DTOs for {@code /items/*} (ONE-38). Inventory items always inline the
 * catalog data the client needs to render — name, description, icon, and
 * the policy fields ({@code consumptionMode}, {@code maxStack}) — so the UI
 * can show, for any item type, "you have N of this thing, max M".
 */
public final class ItemsDto {

    private ItemsDto() {}

    public record CatalogResponse(List<CatalogItem> items, int count) {}

    public record CatalogItem(
            String itemId,
            String name,
            String description,
            String iconUrl,
            String consumptionMode,
            int maxStack,
            String subsystem,
            boolean enabled
    ) {
        public static CatalogItem of(ItemCatalogEntry c) {
            return new CatalogItem(c.itemId, c.name, c.description, c.iconUrl,
                    c.consumptionMode, c.maxStack, c.subsystem, c.enabled);
        }
    }

    public record InventoryItem(
            String itemId,
            String name,
            String description,
            String iconUrl,
            String consumptionMode,
            String subsystem,
            int quantity,
            int maxStack
    ) {
        public static InventoryItem of(UserItem u) {
            ItemCatalogEntry c = u.catalog;
            return new InventoryItem(
                    u.itemId,
                    c == null ? null : c.name,
                    c == null ? null : c.description,
                    c == null ? null : c.iconUrl,
                    c == null ? null : c.consumptionMode,
                    c == null ? null : c.subsystem,
                    u.quantity,
                    c == null ? Integer.MAX_VALUE : c.maxStack);
        }
    }

    /**
     * One unseen consumption notice. {@code dayCovered} is the relevant
     * date (e.g., for the streak protector, the missed day). The client
     * renders one notice per row and calls {@code POST /items/me/consumptions/seen}
     * to dismiss them all at once.
     */
    public record UnseenConsumption(
            Long id,
            String itemId,
            OffsetDateTime consumedAt,
            LocalDate dayCovered,
            String contextRef
    ) {
        public static UnseenConsumption of(ItemConsumption c) {
            return new UnseenConsumption(c.id, c.itemId, c.consumedAt,
                    c.dayCovered, c.contextRef);
        }
    }

    public record MeResponse(
            List<InventoryItem> inventory,
            List<UnseenConsumption> unseenConsumptions,
            int unseenCount
    ) {}

    public record GrantRequest(
            Long userId,
            String itemId,
            Integer quantity,
            String sourceRef
    ) {}

    public record GrantResponse(
            String itemId,
            int newQuantity,
            int maxStack
    ) {}

    public record SeenResponse(int marked) {}
}
