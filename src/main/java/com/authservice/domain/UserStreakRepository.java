package com.authservice.domain;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class UserStreakRepository {

    private final JdbcTemplate jdbc;

    public UserStreakRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<UserStreak> findByUserId(Long userId) {
        List<UserStreak> rows = jdbc.query(
                "SELECT * FROM user_streaks WHERE user_id = ?", mapper(), userId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /**
     * Upsert: insert a fresh row or update the existing one with the new
     * streak state. Returns the persisted row.
     */
    public UserStreak upsert(Long userId, int currentStreak, int longestStreak,
                             LocalDate lastSolveDate, String timezone) {
        jdbc.update(
                "INSERT INTO user_streaks (user_id, current_streak, longest_streak, last_solve_date, last_timezone, started_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, NOW(), NOW()) " +
                "ON CONFLICT (user_id) DO UPDATE SET " +
                "  current_streak = EXCLUDED.current_streak, " +
                "  longest_streak = EXCLUDED.longest_streak, " +
                "  last_solve_date = EXCLUDED.last_solve_date, " +
                "  last_timezone = EXCLUDED.last_timezone, " +
                "  started_at = CASE " +
                "    WHEN user_streaks.current_streak = 0 OR EXCLUDED.current_streak = 1 THEN NOW() " +
                "    ELSE user_streaks.started_at " +
                "  END, " +
                "  updated_at = NOW()",
                userId, currentStreak, longestStreak,
                lastSolveDate != null ? Date.valueOf(lastSolveDate) : null,
                timezone);
        return findByUserId(userId).orElseThrow();
    }

    private RowMapper<UserStreak> mapper() {
        return (rs, rowNum) -> {
            UserStreak s = new UserStreak();
            s.setUserId(rs.getLong("user_id"));
            s.setCurrentStreak(rs.getInt("current_streak"));
            s.setLongestStreak(rs.getInt("longest_streak"));
            Date lsd = rs.getDate("last_solve_date");
            if (lsd != null) s.setLastSolveDate(lsd.toLocalDate());
            s.setLastTimezone(rs.getString("last_timezone"));
            if (rs.getTimestamp("started_at") != null) {
                s.setStartedAt(rs.getTimestamp("started_at").toLocalDateTime());
            }
            s.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
            return s;
        };
    }
}
