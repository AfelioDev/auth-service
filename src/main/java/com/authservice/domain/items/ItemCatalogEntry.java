package com.authservice.domain.items;

import java.time.OffsetDateTime;

/**
 * One row of the server-owned item catalog (ONE-38). The catalog is the
 * source of truth for every item that exists in the system; the client
 * never assumes which items exist or what they do.
 */
public final class ItemCatalogEntry {
    public String itemId;
    public String name;
    public String description;
    public String iconUrl;
    public String consumptionMode;   // AUTO | MANUAL
    public int maxStack;
    public String subsystem;         // STREAK | ...
    public boolean enabled;
    public OffsetDateTime createdAt;
    public OffsetDateTime updatedAt;
}
