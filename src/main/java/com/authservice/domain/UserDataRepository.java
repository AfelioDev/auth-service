package com.authservice.domain;

import com.authservice.domain.UserData.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Bulk repository for the user-data sync feature (Tarea 59 / ONE-27).
 *
 * One JDBC class for all six tables to avoid file explosion. Each table
 * has roughly the same shape (find-by-user, upsert-by-clientId, delete-by-clientId)
 * so consolidating keeps the code smaller and the patterns visible.
 */
@Repository
public class UserDataRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public UserDataRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    // ── Solves ────────────────────────────────────────────────────────────

    public List<Solve> findSolvesByUser(Long userId, OffsetDateTime since, int limit) {
        String sql = "SELECT * FROM user_solves WHERE user_id = ? " +
                (since != null ? "AND updated_at > ? " : "") +
                "ORDER BY updated_at ASC LIMIT ?";
        if (since != null) {
            return jdbc.query(sql, solveMapper(), userId, Timestamp.from(since.toInstant()), limit);
        }
        return jdbc.query(sql, solveMapper(), userId, limit);
    }

    /** Idempotent batch upsert by (user_id, client_id). */
    public int upsertSolves(Long userId, List<Solve> solves) {
        int count = 0;
        for (Solve s : solves) {
            jdbc.update(
                    "INSERT INTO user_solves (user_id, client_id, session_client_id, solve_time_ms, " +
                    "  solved_at, scramble, scramble_image_svg, is_ok, has_plus2, has_dnf, is_favorite, " +
                    "  is_deleted, comments, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW()) " +
                    "ON CONFLICT (user_id, client_id) DO UPDATE SET " +
                    "  session_client_id = EXCLUDED.session_client_id, " +
                    "  solve_time_ms = EXCLUDED.solve_time_ms, " +
                    "  solved_at = EXCLUDED.solved_at, " +
                    "  scramble = EXCLUDED.scramble, " +
                    "  scramble_image_svg = EXCLUDED.scramble_image_svg, " +
                    "  is_ok = EXCLUDED.is_ok, " +
                    "  has_plus2 = EXCLUDED.has_plus2, " +
                    "  has_dnf = EXCLUDED.has_dnf, " +
                    "  is_favorite = EXCLUDED.is_favorite, " +
                    "  is_deleted = EXCLUDED.is_deleted, " +
                    "  comments = EXCLUDED.comments, " +
                    "  updated_at = NOW()",
                    userId, s.clientId, s.sessionClientId, s.solveTimeMs,
                    s.solvedAt != null ? Timestamp.from(s.solvedAt.toInstant()) : null,
                    s.scramble, s.scrambleImageSvg,
                    bool(s.isOk, true), bool(s.hasPlus2, false), bool(s.hasDnf, false),
                    bool(s.isFavorite, false), bool(s.isDeleted, false),
                    s.comments);
            count++;
        }
        return count;
    }

    /** Soft delete: only the owner can affect their own row. */
    public boolean softDeleteSolve(Long userId, String clientId) {
        return jdbc.update(
                "UPDATE user_solves SET is_deleted = TRUE, updated_at = NOW() " +
                "WHERE user_id = ? AND client_id = ?", userId, clientId) > 0;
    }

    /** Hard wipe (only used by snapshot upload). */
    public int deleteAllSolves(Long userId) {
        return jdbc.update("DELETE FROM user_solves WHERE user_id = ?", userId);
    }

    // ── Sessions ──────────────────────────────────────────────────────────

    public List<Session> findSessionsByUser(Long userId) {
        return jdbc.query("SELECT * FROM user_sessions WHERE user_id = ? ORDER BY created_at ASC",
                sessionMapper(), userId);
    }

    public int upsertSessions(Long userId, List<Session> sessions) {
        int count = 0;
        for (Session s : sessions) {
            jdbc.update(
                    "INSERT INTO user_sessions (user_id, client_id, name, category_id, updated_at) " +
                    "VALUES (?, ?, ?, ?, NOW()) " +
                    "ON CONFLICT (user_id, client_id) DO UPDATE SET " +
                    "  name = EXCLUDED.name, " +
                    "  category_id = EXCLUDED.category_id, " +
                    "  updated_at = NOW()",
                    userId, s.clientId, s.name, s.categoryId);
            count++;
        }
        return count;
    }

    public boolean deleteSession(Long userId, String clientId) {
        // Cascade-delete any solves attached to this session.
        jdbc.update("DELETE FROM user_solves WHERE user_id = ? AND session_client_id = ?",
                userId, clientId);
        return jdbc.update(
                "DELETE FROM user_sessions WHERE user_id = ? AND client_id = ?",
                userId, clientId) > 0;
    }

    public int deleteAllSessions(Long userId) {
        return jdbc.update("DELETE FROM user_sessions WHERE user_id = ?", userId);
    }

    // ── Preferences ───────────────────────────────────────────────────────

    public Preferences findPreferences(Long userId) {
        try {
            return jdbc.queryForObject(
                    "SELECT * FROM user_preferences WHERE user_id = ?",
                    prefsMapper(), userId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public Preferences upsertPreferences(Long userId, Map<String, Object> prefs, boolean replace) {
        String json = toJson(prefs);
        if (replace) {
            jdbc.update(
                    "INSERT INTO user_preferences (user_id, preferences, updated_at) " +
                    "VALUES (?, ?::jsonb, NOW()) " +
                    "ON CONFLICT (user_id) DO UPDATE SET " +
                    "  preferences = EXCLUDED.preferences, " +
                    "  updated_at = NOW()",
                    userId, json);
        } else {
            // PATCH: deep-merge JSONB so missing keys are preserved.
            jdbc.update(
                    "INSERT INTO user_preferences (user_id, preferences, updated_at) " +
                    "VALUES (?, ?::jsonb, NOW()) " +
                    "ON CONFLICT (user_id) DO UPDATE SET " +
                    "  preferences = user_preferences.preferences || EXCLUDED.preferences, " +
                    "  updated_at = NOW()",
                    userId, json);
        }
        return findPreferences(userId);
    }

    public int deletePreferences(Long userId) {
        return jdbc.update("DELETE FROM user_preferences WHERE user_id = ?", userId);
    }

    // ── Profile ───────────────────────────────────────────────────────────

    public Profile findProfile(Long userId) {
        try {
            return jdbc.queryForObject(
                    "SELECT * FROM user_profile WHERE user_id = ?",
                    profileMapper(), userId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public Profile upsertProfile(Long userId, String displayName) {
        jdbc.update(
                "INSERT INTO user_profile (user_id, display_name, display_name_updated_at, updated_at) " +
                "VALUES (?, ?, NOW(), NOW()) " +
                "ON CONFLICT (user_id) DO UPDATE SET " +
                "  display_name = EXCLUDED.display_name, " +
                "  display_name_updated_at = NOW(), " +
                "  updated_at = NOW()",
                userId, displayName);
        return findProfile(userId);
    }

    public int deleteProfile(Long userId) {
        return jdbc.update("DELETE FROM user_profile WHERE user_id = ?", userId);
    }

    /**
     * Loads every non-deleted solve for the user, flattened with its session's
     * category, in chronological order. Used to compute in-app records (best
     * single + best Ao5 per category) for the profile page — including when
     * a third party visits the profile.
     *
     * Solves whose session_client_id appears in `excludedSessionClientIds`
     * are filtered out at the SQL level so the caller never sees them.
     */
    public List<UserData.RecordSolve> findRecordSolvesByUser(Long userId,
                                                              java.util.Collection<String> excludedSessionClientIds) {
        StringBuilder sql = new StringBuilder(
                "SELECT s.category_id, sv.session_client_id, sv.solve_time_ms, " +
                "       sv.has_plus2, sv.has_dnf, sv.solved_at, sv.scramble " +
                "FROM user_solves sv " +
                "JOIN user_sessions s ON s.user_id = sv.user_id AND s.client_id = sv.session_client_id " +
                "WHERE sv.user_id = ? AND sv.is_deleted = FALSE ");
        java.util.List<Object> params = new java.util.ArrayList<>();
        params.add(userId);

        if (excludedSessionClientIds != null && !excludedSessionClientIds.isEmpty()) {
            String placeholders = String.join(",",
                    java.util.Collections.nCopies(excludedSessionClientIds.size(), "?"));
            sql.append("AND sv.session_client_id NOT IN (").append(placeholders).append(") ");
            params.addAll(excludedSessionClientIds);
        }
        sql.append("ORDER BY sv.solved_at ASC NULLS LAST, sv.id ASC");

        return jdbc.query(sql.toString(), (rs, n) -> new UserData.RecordSolve(
                rs.getString("category_id"),
                rs.getString("session_client_id"),
                rs.getInt("solve_time_ms"),
                rs.getBoolean("has_plus2"),
                rs.getBoolean("has_dnf"),
                ts(rs.getTimestamp("solved_at")),
                rs.getString("scramble")
        ), params.toArray());
    }

    /** Reads `users.name` (the WCA name) — used for displayName validation. */
    public Optional<String> findUserWcaName(Long userId) {
        List<String> rows = jdbc.queryForList(
                "SELECT name FROM users WHERE id = ?", String.class, userId);
        return rows.isEmpty() ? Optional.empty() : Optional.ofNullable(rows.get(0));
    }

    // ── Learn ─────────────────────────────────────────────────────────────

    public List<LearnedAlgorithm> findLearnByUser(Long userId) {
        return jdbc.query(
                "SELECT * FROM user_learned_algorithms WHERE user_id = ? ORDER BY learned_at ASC",
                learnMapper(), userId);
    }

    public boolean addLearn(Long userId, String caseId, String submethodId) {
        return jdbc.update(
                "INSERT INTO user_learned_algorithms (user_id, case_id, submethod_id) " +
                "VALUES (?, ?, ?) " +
                "ON CONFLICT (user_id, case_id, submethod_id) DO NOTHING",
                userId, caseId, submethodId) > 0;
    }

    public boolean removeLearn(Long userId, String caseId, String submethodId) {
        return jdbc.update(
                "DELETE FROM user_learned_algorithms " +
                "WHERE user_id = ? AND case_id = ? AND submethod_id = ?",
                userId, caseId, submethodId) > 0;
    }

    public int deleteAllLearn(Long userId) {
        return jdbc.update("DELETE FROM user_learned_algorithms WHERE user_id = ?", userId);
    }

    // ── Row mappers ───────────────────────────────────────────────────────

    private RowMapper<Solve> solveMapper() {
        return (rs, n) -> {
            Solve s = new Solve();
            s.id = rs.getLong("id");
            s.userId = rs.getLong("user_id");
            s.clientId = rs.getString("client_id");
            s.sessionClientId = rs.getString("session_client_id");
            s.solveTimeMs = rs.getInt("solve_time_ms");
            s.solvedAt = ts(rs.getTimestamp("solved_at"));
            s.scramble = rs.getString("scramble");
            s.scrambleImageSvg = rs.getString("scramble_image_svg");
            s.isOk = rs.getBoolean("is_ok");
            s.hasPlus2 = rs.getBoolean("has_plus2");
            s.hasDnf = rs.getBoolean("has_dnf");
            s.isFavorite = rs.getBoolean("is_favorite");
            s.isDeleted = rs.getBoolean("is_deleted");
            s.comments = rs.getString("comments");
            s.createdAt = ts(rs.getTimestamp("created_at"));
            s.updatedAt = ts(rs.getTimestamp("updated_at"));
            return s;
        };
    }

    private RowMapper<Session> sessionMapper() {
        return (rs, n) -> {
            Session s = new Session();
            s.id = rs.getLong("id");
            s.userId = rs.getLong("user_id");
            s.clientId = rs.getString("client_id");
            s.name = rs.getString("name");
            s.categoryId = rs.getString("category_id");
            s.createdAt = ts(rs.getTimestamp("created_at"));
            s.updatedAt = ts(rs.getTimestamp("updated_at"));
            return s;
        };
    }

    private RowMapper<Preferences> prefsMapper() {
        return (rs, n) -> {
            Preferences p = new Preferences();
            p.userId = rs.getLong("user_id");
            p.preferences = fromJson(rs.getString("preferences"));
            p.updatedAt = ts(rs.getTimestamp("updated_at"));
            return p;
        };
    }

    private RowMapper<Profile> profileMapper() {
        return (rs, n) -> {
            Profile p = new Profile();
            p.userId = rs.getLong("user_id");
            p.displayName = rs.getString("display_name");
            p.displayNameUpdatedAt = ts(rs.getTimestamp("display_name_updated_at"));
            p.updatedAt = ts(rs.getTimestamp("updated_at"));
            return p;
        };
    }

    private RowMapper<LearnedAlgorithm> learnMapper() {
        return (rs, n) -> {
            LearnedAlgorithm l = new LearnedAlgorithm();
            l.id = rs.getLong("id");
            l.userId = rs.getLong("user_id");
            l.caseId = rs.getString("case_id");
            l.submethodId = rs.getString("submethod_id");
            l.learnedAt = ts(rs.getTimestamp("learned_at"));
            return l;
        };
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static OffsetDateTime ts(Timestamp t) {
        return t == null ? null : t.toInstant().atOffset(ZoneOffset.UTC);
    }

    private static Boolean bool(Boolean b, boolean fallback) {
        return b != null ? b : fallback;
    }

    private String toJson(Map<String, Object> m) {
        try {
            return objectMapper.writeValueAsString(m == null ? new HashMap<>() : m);
        } catch (Exception e) {
            throw new RuntimeException("failed to serialize preferences", e);
        }
    }

    private Map<String, Object> fromJson(String json) {
        if (json == null || json.isBlank()) return new HashMap<>();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return new HashMap<>();
        }
    }
}
