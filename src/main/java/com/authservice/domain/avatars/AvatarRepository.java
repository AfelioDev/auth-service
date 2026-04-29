package com.authservice.domain.avatars;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * Data access for the avatar system (Tarea 14 / ONE-14).
 *
 * Reads always join the catalog so a UserAvatar instance carries enough info
 * for the API to render without an extra round-trip. Writes are split into
 * the smallest atomic units the service layer needs to compose under
 * {@code @Transactional}.
 */
@Repository
public class AvatarRepository {

    private final JdbcTemplate jdbc;

    public AvatarRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ── Catalog ──────────────────────────────────────────────────────────

    public List<AvatarCatalogEntry> findAllCatalog() {
        return jdbc.query(
                "SELECT * FROM avatars_catalog ORDER BY rarity, avatar_id",
                catalogMapper());
    }

    public List<AvatarCatalogEntry> findInitialFreeCatalog() {
        return jdbc.query(
                "SELECT * FROM avatars_catalog WHERE is_initial_free = TRUE " +
                "ORDER BY rarity, avatar_id",
                catalogMapper());
    }

    public Optional<AvatarCatalogEntry> findCatalogById(String avatarId) {
        try {
            return Optional.of(jdbc.queryForObject(
                    "SELECT * FROM avatars_catalog WHERE avatar_id = ?",
                    catalogMapper(), avatarId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    // ── Inventory ────────────────────────────────────────────────────────

    /**
     * Returns every avatar the user owns, joined with catalog data so the
     * caller can render directly. Ordered by acquisition time (oldest first).
     */
    public List<UserAvatar> findInventory(Long userId) {
        return jdbc.query(
                "SELECT ua.user_id, ua.avatar_id, ua.acquisition_source, ua.acquired_at, " +
                "       c.name AS c_name, c.description AS c_description, c.rarity AS c_rarity, " +
                "       c.image_url AS c_image_url, c.is_initial_free AS c_is_initial_free, " +
                "       c.created_at AS c_created_at, c.updated_at AS c_updated_at " +
                "  FROM user_avatars ua " +
                "  JOIN avatars_catalog c ON c.avatar_id = ua.avatar_id " +
                " WHERE ua.user_id = ? " +
                " ORDER BY ua.acquired_at ASC",
                inventoryMapper(), userId);
    }

    public boolean hasAvatar(Long userId, String avatarId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_avatars WHERE user_id = ? AND avatar_id = ?",
                Integer.class, userId, avatarId);
        return n != null && n > 0;
    }

    public int countInventory(Long userId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_avatars WHERE user_id = ?",
                Integer.class, userId);
        return n == null ? 0 : n;
    }

    public Optional<UserAvatar> findInventoryEntry(Long userId, String avatarId) {
        try {
            return Optional.of(jdbc.queryForObject(
                    "SELECT ua.user_id, ua.avatar_id, ua.acquisition_source, ua.acquired_at, " +
                    "       c.name AS c_name, c.description AS c_description, c.rarity AS c_rarity, " +
                    "       c.image_url AS c_image_url, c.is_initial_free AS c_is_initial_free, " +
                    "       c.created_at AS c_created_at, c.updated_at AS c_updated_at " +
                    "  FROM user_avatars ua " +
                    "  JOIN avatars_catalog c ON c.avatar_id = ua.avatar_id " +
                    " WHERE ua.user_id = ? AND ua.avatar_id = ?",
                    inventoryMapper(), userId, avatarId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public void insertInventory(Long userId, String avatarId, String source) {
        jdbc.update(
                "INSERT INTO user_avatars (user_id, avatar_id, acquisition_source) VALUES (?, ?, ?)",
                userId, avatarId, source);
    }

    public boolean deleteInventory(Long userId, String avatarId) {
        return jdbc.update(
                "DELETE FROM user_avatars WHERE user_id = ? AND avatar_id = ?",
                userId, avatarId) > 0;
    }

    // ── State ────────────────────────────────────────────────────────────

    public Optional<UserAvatarState> findState(Long userId) {
        try {
            return Optional.of(jdbc.queryForObject(
                    "SELECT * FROM user_avatar_state WHERE user_id = ?",
                    stateMapper(), userId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /**
     * Initial onboarding insert. Called inside the same transaction as the
     * inventory insert, so on failure either both go in or neither does.
     */
    public void insertOnboardingState(Long userId, String avatarId) {
        jdbc.update(
                "INSERT INTO user_avatar_state (" +
                "    user_id, equipped_avatar_id, initial_free_acquired_at, " +
                "    initial_free_changes_used, updated_at) " +
                "VALUES (?, ?, NOW(), 0, NOW())",
                userId, avatarId);
    }

    public void updateEquipped(Long userId, String avatarId) {
        int n = jdbc.update(
                "UPDATE user_avatar_state SET equipped_avatar_id = ?, updated_at = NOW() " +
                "WHERE user_id = ?",
                avatarId, userId);
        if (n == 0) {
            // Defensive: state row should exist for any user who has avatars.
            // If it doesn't, create it without onboarding timestamp (legacy users).
            jdbc.update(
                    "INSERT INTO user_avatar_state (user_id, equipped_avatar_id, " +
                    "    initial_free_changes_used, updated_at) VALUES (?, ?, 0, NOW())",
                    userId, avatarId);
        }
    }

    /**
     * Bumps the initial-free change counter and updates the equipped pointer
     * if needed. Called inside the same transaction as the inventory swap.
     */
    public void recordInitialFreeChange(Long userId, String newAvatarId, boolean replaceEquipped) {
        if (replaceEquipped) {
            jdbc.update(
                    "UPDATE user_avatar_state SET " +
                    "    equipped_avatar_id = ?, " +
                    "    initial_free_changes_used = initial_free_changes_used + 1, " +
                    "    last_initial_free_change_at = NOW(), " +
                    "    updated_at = NOW() " +
                    "WHERE user_id = ?",
                    newAvatarId, userId);
        } else {
            jdbc.update(
                    "UPDATE user_avatar_state SET " +
                    "    initial_free_changes_used = initial_free_changes_used + 1, " +
                    "    last_initial_free_change_at = NOW(), " +
                    "    updated_at = NOW() " +
                    "WHERE user_id = ?",
                    userId);
        }
    }

    /** Resolves the image URL for a user's currently equipped avatar, if any. */
    public Optional<String> findEquippedImageUrl(Long userId) {
        try {
            String url = jdbc.queryForObject(
                    "SELECT c.image_url FROM user_avatar_state s " +
                    "  JOIN avatars_catalog c ON c.avatar_id = s.equipped_avatar_id " +
                    " WHERE s.user_id = ?",
                    String.class, userId);
            return Optional.ofNullable(url);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    // ── Initial-free change options ──────────────────────────────────────

    /**
     * Avatars currently flagged as initial-free that the user does NOT have
     * in their inventory by any acquisition source.
     */
    public List<AvatarCatalogEntry> findInitialChangeOptions(Long userId) {
        return jdbc.query(
                "SELECT c.* FROM avatars_catalog c " +
                " WHERE c.is_initial_free = TRUE " +
                "   AND NOT EXISTS (" +
                "       SELECT 1 FROM user_avatars ua " +
                "        WHERE ua.user_id = ? AND ua.avatar_id = c.avatar_id) " +
                " ORDER BY c.rarity, c.avatar_id",
                catalogMapper(), userId);
    }

    /** The user's INITIAL_FREE inventory entry, if any. */
    public Optional<UserAvatar> findInitialFreeEntry(Long userId) {
        try {
            return Optional.of(jdbc.queryForObject(
                    "SELECT ua.user_id, ua.avatar_id, ua.acquisition_source, ua.acquired_at, " +
                    "       c.name AS c_name, c.description AS c_description, c.rarity AS c_rarity, " +
                    "       c.image_url AS c_image_url, c.is_initial_free AS c_is_initial_free, " +
                    "       c.created_at AS c_created_at, c.updated_at AS c_updated_at " +
                    "  FROM user_avatars ua " +
                    "  JOIN avatars_catalog c ON c.avatar_id = ua.avatar_id " +
                    " WHERE ua.user_id = ? AND ua.acquisition_source = 'INITIAL_FREE'",
                    inventoryMapper(), userId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    // ── Mappers ──────────────────────────────────────────────────────────

    private RowMapper<AvatarCatalogEntry> catalogMapper() {
        return (rs, n) -> {
            AvatarCatalogEntry c = new AvatarCatalogEntry();
            c.avatarId = rs.getString("avatar_id");
            c.name = rs.getString("name");
            c.description = rs.getString("description");
            c.rarity = rs.getString("rarity");
            c.imageUrl = rs.getString("image_url");
            c.isInitialFree = rs.getBoolean("is_initial_free");
            c.createdAt = ts(rs.getTimestamp("created_at"));
            c.updatedAt = ts(rs.getTimestamp("updated_at"));
            return c;
        };
    }

    private RowMapper<UserAvatar> inventoryMapper() {
        return (rs, n) -> {
            UserAvatar u = new UserAvatar();
            u.userId = rs.getLong("user_id");
            u.avatarId = rs.getString("avatar_id");
            u.acquisitionSource = rs.getString("acquisition_source");
            u.acquiredAt = ts(rs.getTimestamp("acquired_at"));
            AvatarCatalogEntry c = new AvatarCatalogEntry();
            c.avatarId = u.avatarId;
            c.name = rs.getString("c_name");
            c.description = rs.getString("c_description");
            c.rarity = rs.getString("c_rarity");
            c.imageUrl = rs.getString("c_image_url");
            c.isInitialFree = rs.getBoolean("c_is_initial_free");
            c.createdAt = ts(rs.getTimestamp("c_created_at"));
            c.updatedAt = ts(rs.getTimestamp("c_updated_at"));
            u.catalog = c;
            return u;
        };
    }

    private RowMapper<UserAvatarState> stateMapper() {
        return (rs, n) -> {
            UserAvatarState s = new UserAvatarState();
            s.userId = rs.getLong("user_id");
            s.equippedAvatarId = rs.getString("equipped_avatar_id");
            s.initialFreeAcquiredAt = ts(rs.getTimestamp("initial_free_acquired_at"));
            s.initialFreeChangesUsed = rs.getInt("initial_free_changes_used");
            s.lastInitialFreeChangeAt = ts(rs.getTimestamp("last_initial_free_change_at"));
            s.updatedAt = ts(rs.getTimestamp("updated_at"));
            return s;
        };
    }

    private static OffsetDateTime ts(Timestamp t) {
        return t == null ? null : t.toInstant().atOffset(ZoneOffset.UTC);
    }
}
