package com.authservice.domain.avatars;

import java.time.OffsetDateTime;

/**
 * One inventory entry: the user owns this catalog avatar via the recorded
 * {@code acquisitionSource}. Only entries with source {@code INITIAL_FREE}
 * are eligible to be removed by the initial-free change flow.
 */
public final class UserAvatar {
    public Long userId;
    public String avatarId;
    public String acquisitionSource; // INITIAL_FREE | PURCHASE
    public OffsetDateTime acquiredAt;

    /** Catalog enrichment, populated when the row is read joined against avatars_catalog. */
    public AvatarCatalogEntry catalog;
}
