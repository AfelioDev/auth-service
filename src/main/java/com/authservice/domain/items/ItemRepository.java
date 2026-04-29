package com.authservice.domain.items;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * Data access for the item inventory system (ONE-38). Reads against the
 * inventory always join the catalog so the service can render without a
 * second round-trip. Writes are split into the smallest atomic units the
 * service composes under {@code @Transactional}.
 */
@Repository
public class ItemRepository {

    private final JdbcTemplate jdbc;

    public ItemRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ── Catalog ──────────────────────────────────────────────────────────

    public List<ItemCatalogEntry> findAllCatalog() {
        return jdbc.query(
                "SELECT * FROM items_catalog ORDER BY subsystem, item_id",
                catalogMapper());
    }

    public Optional<ItemCatalogEntry> findCatalog(String itemId) {
        try {
            return Optional.of(jdbc.queryForObject(
                    "SELECT * FROM items_catalog WHERE item_id = ?",
                    catalogMapper(), itemId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    // ── Inventory reads ──────────────────────────────────────────────────

    public List<UserItem> findInventory(Long userId) {
        return jdbc.query(
                "SELECT ui.user_id, ui.item_id, ui.quantity, ui.updated_at, " +
                "       c.name AS c_name, c.description AS c_description, c.icon_url AS c_icon_url, " +
                "       c.consumption_mode AS c_consumption_mode, c.max_stack AS c_max_stack, " +
                "       c.subsystem AS c_subsystem, c.enabled AS c_enabled, " +
                "       c.created_at AS c_created_at, c.updated_at AS c_updated_at " +
                "  FROM user_items ui " +
                "  JOIN items_catalog c ON c.item_id = ui.item_id " +
                " WHERE ui.user_id = ? " +
                " ORDER BY ui.updated_at DESC, ui.item_id",
                inventoryMapper(), userId);
    }

    /** Returns the user's current quantity of an item, or 0 if not present. */
    public int findQuantity(Long userId, String itemId) {
        Integer q = jdbc.query(
                "SELECT quantity FROM user_items WHERE user_id = ? AND item_id = ?",
                rs -> rs.next() ? rs.getInt("quantity") : 0,
                userId, itemId);
        return q == null ? 0 : q;
    }

    // ── Inventory writes ─────────────────────────────────────────────────

    /**
     * Increases the user's quantity of the given item by {@code delta}.
     * Caller is responsible for catalog/max_stack validation; this method
     * just upserts. Returns the new quantity.
     */
    public int addQuantity(Long userId, String itemId, int delta) {
        if (delta <= 0) {
            throw new IllegalArgumentException("delta must be positive");
        }
        Integer newQ = jdbc.queryForObject(
                "INSERT INTO user_items (user_id, item_id, quantity) VALUES (?, ?, ?) " +
                "ON CONFLICT (user_id, item_id) DO UPDATE SET " +
                "    quantity = user_items.quantity + EXCLUDED.quantity, " +
                "    updated_at = NOW() " +
                "RETURNING quantity",
                Integer.class, userId, itemId, delta);
        return newQ == null ? 0 : newQ;
    }

    /**
     * Decreases the user's quantity by {@code delta}. When the result would
     * be 0, the row is deleted (so "an entry in inventory" always implies
     * {@code quantity > 0}, matching the SQL CHECK). Returns the new
     * quantity, 0 if the row was deleted.
     *
     * Splits delete vs. update to avoid the {@code quantity > 0} CHECK
     * constraint blocking an in-place UPDATE that would reach 0. Caller is
     * expected to be inside a {@code @Transactional} so the read + write
     * are isolated; concurrent decrements on the same (user,item) are
     * extremely rare in practice.
     */
    public int subtractQuantity(Long userId, String itemId, int delta) {
        if (delta <= 0) {
            throw new IllegalArgumentException("delta must be positive");
        }
        int current = findQuantity(userId, itemId);
        if (current < delta) {
            throw new IllegalStateException(
                    "Insufficient quantity to subtract for user=" + userId + " item=" + itemId
                    + " (have=" + current + " want=" + delta + ")");
        }
        if (current == delta) {
            jdbc.update("DELETE FROM user_items WHERE user_id = ? AND item_id = ?", userId, itemId);
            return 0;
        }
        Integer newQ = jdbc.queryForObject(
                "UPDATE user_items SET quantity = quantity - ?, updated_at = NOW() " +
                "WHERE user_id = ? AND item_id = ? RETURNING quantity",
                Integer.class, delta, userId, itemId);
        return newQ == null ? 0 : newQ;
    }

    // ── Acquisition log ──────────────────────────────────────────────────

    public void logAcquisition(Long userId, String itemId, int quantity,
                                String source, String sourceRef) {
        jdbc.update(
                "INSERT INTO item_acquisitions (user_id, item_id, quantity, source, source_ref) " +
                "VALUES (?, ?, ?, ?, ?)",
                userId, itemId, quantity, source, sourceRef);
    }

    // ── Consumption log ──────────────────────────────────────────────────

    public void logConsumption(Long userId, String itemId, LocalDate dayCovered, String contextRef) {
        jdbc.update(
                "INSERT INTO item_consumptions (user_id, item_id, day_covered, context_ref) " +
                "VALUES (?, ?, ?, ?)",
                userId, itemId,
                dayCovered == null ? null : Date.valueOf(dayCovered),
                contextRef);
    }

    public List<ItemConsumption> findUnseenConsumptions(Long userId) {
        return jdbc.query(
                "SELECT * FROM item_consumptions " +
                " WHERE user_id = ? AND seen_by_user_at IS NULL " +
                " ORDER BY consumed_at ASC",
                consumptionMapper(), userId);
    }

    /** Marks all currently-unseen consumptions for the user as seen. Returns affected rows. */
    public int markAllConsumptionsSeen(Long userId) {
        return jdbc.update(
                "UPDATE item_consumptions SET seen_by_user_at = NOW() " +
                "WHERE user_id = ? AND seen_by_user_at IS NULL",
                userId);
    }

    // ── Mappers ──────────────────────────────────────────────────────────

    private RowMapper<ItemCatalogEntry> catalogMapper() {
        return (rs, n) -> {
            ItemCatalogEntry c = new ItemCatalogEntry();
            c.itemId = rs.getString("item_id");
            c.name = rs.getString("name");
            c.description = rs.getString("description");
            c.iconUrl = rs.getString("icon_url");
            c.consumptionMode = rs.getString("consumption_mode");
            c.maxStack = rs.getInt("max_stack");
            c.subsystem = rs.getString("subsystem");
            c.enabled = rs.getBoolean("enabled");
            c.createdAt = ts(rs.getTimestamp("created_at"));
            c.updatedAt = ts(rs.getTimestamp("updated_at"));
            return c;
        };
    }

    private RowMapper<UserItem> inventoryMapper() {
        return (rs, n) -> {
            UserItem u = new UserItem();
            u.userId = rs.getLong("user_id");
            u.itemId = rs.getString("item_id");
            u.quantity = rs.getInt("quantity");
            u.updatedAt = ts(rs.getTimestamp("updated_at"));
            ItemCatalogEntry c = new ItemCatalogEntry();
            c.itemId = u.itemId;
            c.name = rs.getString("c_name");
            c.description = rs.getString("c_description");
            c.iconUrl = rs.getString("c_icon_url");
            c.consumptionMode = rs.getString("c_consumption_mode");
            c.maxStack = rs.getInt("c_max_stack");
            c.subsystem = rs.getString("c_subsystem");
            c.enabled = rs.getBoolean("c_enabled");
            c.createdAt = ts(rs.getTimestamp("c_created_at"));
            c.updatedAt = ts(rs.getTimestamp("c_updated_at"));
            u.catalog = c;
            return u;
        };
    }

    private RowMapper<ItemConsumption> consumptionMapper() {
        return (rs, n) -> {
            ItemConsumption c = new ItemConsumption();
            c.id = rs.getLong("id");
            c.userId = rs.getLong("user_id");
            c.itemId = rs.getString("item_id");
            c.consumedAt = ts(rs.getTimestamp("consumed_at"));
            Date d = rs.getDate("day_covered");
            c.dayCovered = d == null ? null : d.toLocalDate();
            c.contextRef = rs.getString("context_ref");
            c.seenByUserAt = ts(rs.getTimestamp("seen_by_user_at"));
            return c;
        };
    }

    private static OffsetDateTime ts(Timestamp t) {
        return t == null ? null : t.toInstant().atOffset(ZoneOffset.UTC);
    }
}
