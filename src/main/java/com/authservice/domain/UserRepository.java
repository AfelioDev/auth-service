package com.authservice.domain;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class UserRepository {

    private final JdbcTemplate jdbc;

    public UserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<User> findById(Long id) {
        List<User> rows = jdbc.query(
                "SELECT * FROM users WHERE id = ?", mapper(), id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public Optional<User> findByEmail(String email) {
        List<User> rows = jdbc.query(
                "SELECT * FROM users WHERE email = ?", mapper(), email);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public Optional<User> findByWcaId(String wcaId) {
        List<User> rows = jdbc.query(
                "SELECT * FROM users WHERE wca_id = ?", mapper(), wcaId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public User save(User user) {
        Long id = jdbc.queryForObject(
                "INSERT INTO users (name, email, password_hash, wca_id, wca_access_token) " +
                "VALUES (?, ?, ?, ?, ?) RETURNING id",
                Long.class,
                user.getName(), user.getEmail(), user.getPasswordHash(),
                user.getWcaId(), user.getWcaAccessToken());
        user.setId(id);
        return user;
    }

    public void updateWcaLink(Long userId, String wcaId, String wcaAccessToken) {
        jdbc.update(
                "UPDATE users SET wca_id = ?, wca_access_token = ?, updated_at = NOW() WHERE id = ?",
                wcaId, wcaAccessToken, userId);
    }

    public void clearWcaLink(Long userId) {
        jdbc.update(
                "UPDATE users SET wca_id = NULL, wca_access_token = NULL, updated_at = NOW() WHERE id = ?",
                userId);
    }

    private RowMapper<User> mapper() {
        return (rs, rowNum) -> {
            User u = new User();
            u.setId(rs.getLong("id"));
            u.setName(rs.getString("name"));
            u.setEmail(rs.getString("email"));
            u.setPasswordHash(rs.getString("password_hash"));
            u.setWcaId(rs.getString("wca_id"));
            u.setWcaAccessToken(rs.getString("wca_access_token"));
            u.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
            u.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
            return u;
        };
    }
}
