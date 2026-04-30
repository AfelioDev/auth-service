package com.authservice.domain;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class UserRepository {

    private final JdbcTemplate jdbc;

    public UserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String SELECT_USER_WITH_PROFILE =
            "SELECT u.id, u.name, u.email, u.password_hash, u.wca_account_id, u.wca_id, " +
            "       u.wca_access_token, u.token_version, u.created_at, u.updated_at, " +
            "       u.banned_at, u.ban_reason, u.ban_until, u.friend_code, " +
            "       up.display_name AS profile_display_name " +
            "FROM users u LEFT JOIN user_profile up ON up.user_id = u.id ";

    public Optional<User> findById(Long id) {
        List<User> rows = jdbc.query(
                SELECT_USER_WITH_PROFILE + "WHERE u.id = ?", mapper(), id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public Optional<User> findByEmail(String email) {
        List<User> rows = jdbc.query(
                SELECT_USER_WITH_PROFILE + "WHERE u.email = ?", mapper(), email);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public Optional<User> findByWcaAccountId(Long wcaAccountId) {
        List<User> rows = jdbc.query(
                SELECT_USER_WITH_PROFILE + "WHERE u.wca_account_id = ?", mapper(), wcaAccountId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public Optional<User> findByWcaId(String wcaId) {
        List<User> rows = jdbc.query(
                SELECT_USER_WITH_PROFILE + "WHERE u.wca_id = ?", mapper(), wcaId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public Optional<User> findByFriendCode(String friendCode) {
        List<User> rows = jdbc.query(
                SELECT_USER_WITH_PROFILE + "WHERE u.friend_code = ?", mapper(), friendCode);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /**
     * Batch lookup by WCA competitor IDs. Returns {wcaId → resolvedName}, where
     * resolvedName is the user's chosen display_name override if set, else the
     * WCA name. Used by wca-rest-api to enrich rankings and person profiles
     * with One Timer display name overrides.
     */
    public Map<String, String> findResolvedNamesByWcaIds(List<String> wcaIds) {
        if (wcaIds == null || wcaIds.isEmpty()) return Map.of();
        String placeholders = String.join(",", Collections.nCopies(wcaIds.size(), "?"));
        String sql = "SELECT u.wca_id, u.name, up.display_name AS profile_display_name " +
                     "FROM users u LEFT JOIN user_profile up ON up.user_id = u.id " +
                     "WHERE u.wca_id IN (" + placeholders + ")";
        Map<String, String> out = new HashMap<>();
        jdbc.query(sql, rs -> {
            String wcaId = rs.getString("wca_id");
            String displayName = rs.getString("profile_display_name");
            String name = rs.getString("name");
            String resolved = (displayName != null && !displayName.isBlank()) ? displayName : name;
            if (wcaId != null && resolved != null) out.put(wcaId, resolved);
        }, wcaIds.toArray());
        return out;
    }

    /**
     * Inserts a user. Assigns an 8-digit {@code friend_code} (ONE-40) using
     * {@link java.security.SecureRandom}; on the rare unique-constraint
     * collision the insert is retried up to 5 times. The caller does not
     * need to set the friend_code beforehand.
     */
    public User save(User user) {
        java.security.SecureRandom rnd = new java.security.SecureRandom();
        for (int attempt = 0; attempt < 5; attempt++) {
            String code = String.format("%08d", rnd.nextInt(100_000_000));
            try {
                Long id = jdbc.queryForObject(
                        "INSERT INTO users (name, email, password_hash, wca_account_id, " +
                        "                   wca_id, wca_access_token, friend_code) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id",
                        Long.class,
                        user.getName(), user.getEmail(), user.getPasswordHash(),
                        user.getWcaAccountId(), user.getWcaId(), user.getWcaAccessToken(),
                        code);
                user.setId(id);
                user.setFriendCode(code);
                return user;
            } catch (org.springframework.dao.DuplicateKeyException dup) {
                // Could be friend_code collision (retry) OR email/wcaAccountId
                // collision (do not retry — let it propagate).
                if (!dup.getMessage().toLowerCase().contains("friend_code")) {
                    throw dup;
                }
            }
        }
        throw new IllegalStateException(
                "Could not assign a unique friend_code after 5 attempts");
    }

    public void updateWcaLink(Long userId, Long wcaAccountId, String wcaId, String wcaAccessToken) {
        jdbc.update(
                "UPDATE users SET wca_account_id = ?, wca_id = ?, wca_access_token = ?, updated_at = NOW() WHERE id = ?",
                wcaAccountId, wcaId, wcaAccessToken, userId);
    }

    public void updatePassword(Long userId, String passwordHash) {
        jdbc.update(
                "UPDATE users SET password_hash = ?, updated_at = NOW() WHERE id = ?",
                passwordHash, userId);
    }

    public void clearWcaLink(Long userId) {
        jdbc.update(
                "UPDATE users SET wca_account_id = NULL, wca_id = NULL, wca_access_token = NULL, updated_at = NOW() WHERE id = ?",
                userId);
    }

    /**
     * Sets/updates the ban on a user (Tarea 5 / ONE-9). {@code banUntil}
     * may be null for a permanent ban. Setting {@code reason} and
     * {@code banUntil} to null with this call clears the ban.
     */
    public int setBan(Long userId, String reason, java.time.OffsetDateTime banUntil) {
        if (reason == null) {
            return jdbc.update(
                    "UPDATE users SET banned_at = NULL, ban_reason = NULL, ban_until = NULL, " +
                    "    updated_at = NOW() WHERE id = ?",
                    userId);
        }
        return jdbc.update(
                "UPDATE users SET banned_at = COALESCE(banned_at, NOW()), " +
                "    ban_reason = ?, ban_until = ?, updated_at = NOW() WHERE id = ?",
                reason,
                banUntil == null ? null : java.sql.Timestamp.from(banUntil.toInstant()),
                userId);
    }

    private RowMapper<User> mapper() {
        return (rs, rowNum) -> {
            User u = new User();
            u.setId(rs.getLong("id"));
            u.setName(rs.getString("name"));
            u.setDisplayName(rs.getString("profile_display_name"));
            u.setEmail(rs.getString("email"));
            u.setPasswordHash(rs.getString("password_hash"));
            long wcaAccountId = rs.getLong("wca_account_id");
            u.setWcaAccountId(rs.wasNull() ? null : wcaAccountId);
            u.setWcaId(rs.getString("wca_id"));
            u.setWcaAccessToken(rs.getString("wca_access_token"));
            u.setTokenVersion(rs.getInt("token_version"));
            u.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
            u.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
            java.sql.Timestamp bannedAt = rs.getTimestamp("banned_at");
            u.setBannedAt(bannedAt == null ? null
                    : bannedAt.toInstant().atOffset(java.time.ZoneOffset.UTC));
            u.setBanReason(rs.getString("ban_reason"));
            java.sql.Timestamp banUntil = rs.getTimestamp("ban_until");
            u.setBanUntil(banUntil == null ? null
                    : banUntil.toInstant().atOffset(java.time.ZoneOffset.UTC));
            u.setFriendCode(rs.getString("friend_code"));
            return u;
        };
    }

    public void incrementTokenVersion(Long userId) {
        jdbc.update("UPDATE users SET token_version = token_version + 1, updated_at = NOW() WHERE id = ?", userId);
    }
}
