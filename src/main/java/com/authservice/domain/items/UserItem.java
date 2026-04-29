package com.authservice.domain.items;

import java.time.OffsetDateTime;

/**
 * One inventory entry for a user (ONE-38). Only present while
 * {@code quantity > 0} — the service deletes the row instead of zeroing it.
 */
public final class UserItem {
    public Long userId;
    public String itemId;
    public int quantity;
    public OffsetDateTime updatedAt;

    /** Catalog enrichment populated when the row is read joined against items_catalog. */
    public ItemCatalogEntry catalog;
}
