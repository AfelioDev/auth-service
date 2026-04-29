package com.authservice.domain.avatars;

import java.time.OffsetDateTime;

/**
 * One row of the server-owned avatar catalog (Tarea 14 / ONE-14).
 * The {@code isInitialFree} flag is admin-toggleable at runtime and gates
 * which avatars appear in onboarding and as initial-free change options.
 */
public final class AvatarCatalogEntry {
    public String avatarId;
    public String name;
    public String description;
    public String rarity;            // COMMON | RARE | EPIC | LEGENDARY | MYTHIC
    public String imageUrl;
    public boolean isInitialFree;
    public OffsetDateTime createdAt;
    public OffsetDateTime updatedAt;
}
