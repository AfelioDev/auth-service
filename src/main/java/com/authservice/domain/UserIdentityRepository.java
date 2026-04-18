package com.authservice.domain;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class UserIdentityRepository {

    private final JdbcTemplate jdbc;

    public UserIdentityRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record UserIdentity(
            Long id,
            Long userId,
            String provider,
            String subject,
            String email,
            LocalDateTime linkedAt,
            LocalDateTime lastUsedAt) {}

    public Optional<UserIdentity> find(String provider, String subject) {
        List<UserIdentity> rows = jdbc.query(
                "SELECT * FROM user_identities WHERE provider = ? AND subject = ?",
                (rs, n) -> new UserIdentity(
                        rs.getLong("id"),
                        rs.getLong("user_id"),
                        rs.getString("provider"),
                        rs.getString("subject"),
                        rs.getString("email"),
                        rs.getTimestamp("linked_at").toLocalDateTime(),
                        rs.getTimestamp("last_used_at") != null
                                ? rs.getTimestamp("last_used_at").toLocalDateTime() : null),
                provider, subject);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public List<UserIdentity> findByUser(Long userId) {
        return jdbc.query(
                "SELECT * FROM user_identities WHERE user_id = ? ORDER BY linked_at DESC",
                (rs, n) -> new UserIdentity(
                        rs.getLong("id"),
                        rs.getLong("user_id"),
                        rs.getString("provider"),
                        rs.getString("subject"),
                        rs.getString("email"),
                        rs.getTimestamp("linked_at").toLocalDateTime(),
                        rs.getTimestamp("last_used_at") != null
                                ? rs.getTimestamp("last_used_at").toLocalDateTime() : null),
                userId);
    }

    public void link(Long userId, String provider, String subject, String email) {
        jdbc.update(
                "INSERT INTO user_identities (user_id, provider, subject, email, last_used_at) " +
                "VALUES (?, ?, ?, ?, NOW())",
                userId, provider, subject, email);
    }

    public void touch(Long identityId) {
        jdbc.update("UPDATE user_identities SET last_used_at = NOW() WHERE id = ?", identityId);
    }

    public void unlink(Long userId, String provider) {
        jdbc.update("DELETE FROM user_identities WHERE user_id = ? AND provider = ?", userId, provider);
    }
}
