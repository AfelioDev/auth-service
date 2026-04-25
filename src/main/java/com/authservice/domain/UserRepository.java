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

    public User save(User user) {
        Long id = jdbc.queryForObject(
                "INSERT INTO users (name, email, password_hash, wca_account_id, wca_id, wca_access_token) " +
                "VALUES (?, ?, ?, ?, ?, ?) RETURNING id",
                Long.class,
                user.getName(), user.getEmail(), user.getPasswordHash(),
                user.getWcaAccountId(), user.getWcaId(), user.getWcaAccessToken());
        user.setId(id);
        return user;
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
            return u;
        };
    }

    public void incrementTokenVersion(Long userId) {
        jdbc.update("UPDATE users SET token_version = token_version + 1, updated_at = NOW() WHERE id = ?", userId);
    }
}
