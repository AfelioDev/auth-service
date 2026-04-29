package com.authservice.api.dto;

import com.authservice.domain.avatars.AvatarCatalogEntry;
import com.authservice.domain.avatars.UserAvatar;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * DTOs for {@code /avatars/*} (Tarea 14 / ONE-14). All response shapes are
 * intentionally explicit so the client never has to infer fields from
 * catalog state — what comes over the wire is what the UI renders.
 */
public final class AvatarsDto {

    private AvatarsDto() {}

    public record CatalogResponse(List<CatalogItem> avatars, int count) {}

    /** Catalog item shape — same fields whether listing all or just initial-free. */
    public record CatalogItem(
            String avatarId,
            String name,
            String description,
            String rarity,
            String imageUrl,
            boolean isInitialFree
    ) {
        public static CatalogItem of(AvatarCatalogEntry c) {
            return new CatalogItem(c.avatarId, c.name, c.description, c.rarity,
                    c.imageUrl, c.isInitialFree);
        }
    }

    /** Inventory entry returned in {@code GET /avatars/me}. */
    public record InventoryItem(
            String avatarId,
            String name,
            String description,
            String rarity,
            String imageUrl,
            boolean isInitialFree,
            String acquisitionSource,
            OffsetDateTime acquiredAt
    ) {
        public static InventoryItem of(UserAvatar u) {
            AvatarCatalogEntry c = u.catalog;
            return new InventoryItem(
                    u.avatarId,
                    c == null ? null : c.name,
                    c == null ? null : c.description,
                    c == null ? null : c.rarity,
                    c == null ? null : c.imageUrl,
                    c != null && c.isInitialFree,
                    u.acquisitionSource,
                    u.acquiredAt);
        }
    }

    /**
     * Full snapshot for the current user. {@code mustOnboard} is a
     * convenience flag derived from {@code inventory.isEmpty()} so the
     * client doesn't have to compute it.
     */
    public record MeResponse(
            boolean mustOnboard,
            List<InventoryItem> inventory,
            @JsonInclude(JsonInclude.Include.NON_NULL) InventoryItem equipped,
            InitialChangeStatus initialChange
    ) {}

    /**
     * Status of the initial-free change feature for this user.
     * {@code nextChangeOpensAt} is a UTC timestamp; the client converts to
     * "X days" / "X hours" locally. {@code null} means "open now" (or no
     * remaining changes — distinguish via {@code changesRemaining}).
     */
    public record InitialChangeStatus(
            int changesRemaining,                                            // 0, 1, or 2
            int changesUsed,                                                 // 0, 1, or 2
            @JsonInclude(JsonInclude.Include.NON_NULL) OffsetDateTime nextChangeOpensAt,
            boolean isOpen,
            String reason                                                    // OPEN | WINDOW_NOT_OPEN | NO_CHANGES_LEFT
    ) {}

    public record InitialPickRequest(String avatarId) {}

    public record EquipRequest(String avatarId) {}

    public record InitialChangeRequest(String newAvatarId) {}

    /**
     * Response for {@code GET /avatars/me/initial-change-options}. When
     * {@code options} is empty, {@code emptyReason} distinguishes between
     * "user already owns every initial-free avatar" and the window not being
     * open or having no changes remaining.
     */
    public record InitialChangeOptionsResponse(
            InitialChangeStatus status,
            List<CatalogItem> options,
            int count,
            @JsonInclude(JsonInclude.Include.NON_NULL) String emptyReason   // ALL_OWNED | null
    ) {}
}
