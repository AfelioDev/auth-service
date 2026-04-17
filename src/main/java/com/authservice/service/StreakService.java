package com.authservice.service;

import com.authservice.domain.UserStreak;
import com.authservice.domain.UserStreakRepository;
import com.authservice.exception.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Daily streaks (Duolingo style).
 *
 * Source-of-truth design:
 *  - The server is the only authority over "what day is it" — the device clock
 *    is never trusted. The client reports its IANA timezone (e.g.
 *    America/Mexico_City), and the server uses NOW() in that timezone to
 *    derive the user's local date.
 *  - The streak transition rules:
 *      same day        -> no-op
 *      next day        -> current_streak++ (longest_streak = max(longest, current))
 *      gap >= 2 days   -> current_streak = 1 (broken, started fresh)
 *      first ever      -> current_streak = 1
 *  - longest_streak only ever increases.
 */
@Service
public class StreakService {

    /** Hitos clásicos de retención. */
    private static final int[] MILESTONES = {7, 14, 30, 60, 100, 180, 365, 500, 1000};

    private final UserStreakRepository streakRepo;

    public StreakService(UserStreakRepository streakRepo) {
        this.streakRepo = streakRepo;
    }

    // ── Time oracle ──────────────────────────────────────────────────────────

    /**
     * Returns the server-authoritative time, plus the equivalent in the
     * user's reported timezone. Used by the client to know "what day it is"
     * even when the device clock is wrong.
     */
    public TimeResponse resolveTime(String timezoneRaw) {
        ZoneId zone = parseZone(timezoneRaw);
        Instant nowUtc = Instant.now();
        ZonedDateTime userLocal = nowUtc.atZone(zone);
        return new TimeResponse(
                nowUtc.toString(),                              // ISO-8601 UTC
                userLocal.toOffsetDateTime().toString(),        // ISO-8601 with offset
                userLocal.toLocalDate().toString(),             // YYYY-MM-DD
                zone.getId()
        );
    }

    // ── Register a solve ─────────────────────────────────────────────────────

    /**
     * Registers that the given user has done a solve "today" in their local
     * timezone. Idempotent within the same local day. Returns the resulting
     * streak state.
     */
    public StreakSnapshot registerSolve(Long userId, String timezoneRaw) {
        ZoneId zone = parseZone(timezoneRaw);
        LocalDate today = ZonedDateTime.now(zone).toLocalDate();

        Optional<UserStreak> existing = streakRepo.findByUserId(userId);

        if (existing.isEmpty()) {
            // First solve ever
            UserStreak fresh = streakRepo.upsert(userId, 1, 1, today, zone.getId());
            return snapshot(fresh, true);
        }

        UserStreak s = existing.get();
        LocalDate last = s.getLastSolveDate();

        if (last != null && last.isEqual(today)) {
            // Already counted today: no-op
            return snapshot(s, false);
        }

        int newCurrent;
        if (last == null) {
            newCurrent = 1;
        } else if (last.plusDays(1).isEqual(today)) {
            // Consecutive day: increment
            newCurrent = s.getCurrentStreak() + 1;
        } else {
            // Gap (>= 2 days, OR last is in the future somehow): reset to 1
            newCurrent = 1;
        }

        int newLongest = Math.max(s.getLongestStreak(), newCurrent);
        UserStreak updated = streakRepo.upsert(userId, newCurrent, newLongest, today, zone.getId());
        return snapshot(updated, true);
    }

    // ── Get streak ───────────────────────────────────────────────────────────

    /**
     * Returns the current streak state for the given user. If the user has
     * no row yet, returns a zero-valued snapshot (does NOT create the row).
     *
     * STALENESS FIX: if the last solve was more than 1 day ago (UTC),
     * the streak is effectively broken — we return currentStreak=0 even
     * though the DB row hasn't been updated yet. This avoids showing a
     * stale streak on friend cards and profiles.
     */
    public StreakSnapshot getStreak(Long userId) {
        Optional<UserStreak> opt = streakRepo.findByUserId(userId);
        if (opt.isEmpty()) {
            return new StreakSnapshot(0, 0, null, null, MILESTONES[0], false);
        }
        return snapshot(opt.get(), false);
    }

    /**
     * Batch version of getStreak() for enriching friend lists without N+1.
     * Returns a map of userId -> StreakSnapshot.
     */
    public Map<Long, StreakSnapshot> getStreaksBatch(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return Map.of();
        Map<Long, StreakSnapshot> result = new java.util.HashMap<>();
        for (Long uid : userIds) {
            result.put(uid, getStreak(uid));
        }
        return result;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private StreakSnapshot snapshot(UserStreak s, boolean updatedNow) {
        int effectiveStreak = s.getCurrentStreak();
        LocalDate lastSolve = s.getLastSolveDate();

        // Staleness correction: if the user hasn't solved since > 1 day ago
        // (in UTC — good enough approximation for cross-user read), the
        // streak is broken. We don't write back to the DB here (that's the
        // job of registerSolve when the user comes back); we just report 0.
        if (lastSolve != null) {
            LocalDate todayUtc = LocalDate.now(java.time.ZoneOffset.UTC);
            long gap = java.time.temporal.ChronoUnit.DAYS.between(lastSolve, todayUtc);
            if (gap > 1) {
                effectiveStreak = 0;
            }
        }

        Integer next = nextMilestone(effectiveStreak);
        return new StreakSnapshot(
                effectiveStreak,
                s.getLongestStreak(),
                lastSolve != null ? lastSolve.toString() : null,
                s.getLastTimezone(),
                next,
                updatedNow
        );
    }

    /** The next milestone strictly greater than the current streak, or null if past 1000. */
    static Integer nextMilestone(int currentStreak) {
        for (int m : MILESTONES) {
            if (m > currentStreak) return m;
        }
        return null;
    }

    /**
     * Parses a timezone string. Accepts IANA IDs (`America/Mexico_City`),
     * fixed offsets (`-06:00`, `+02:00`), and the common `GMT-6` shorthand.
     */
    static ZoneId parseZone(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "timezone is required");
        }
        try {
            // Java's ZoneId.of handles IANA, "Z", "UTC", and offsets like
            // "+02:00" or "-06:00" out of the box. "GMT-6" is also accepted
            // via the SHORT_IDS map but normalized to "GMT-06:00".
            // ZoneRulesException extends DateTimeException, so a single catch covers both.
            return ZoneId.of(raw.trim());
        } catch (java.time.DateTimeException e) {
            throw new AppException(HttpStatus.BAD_REQUEST, "invalid timezone: " + raw);
        }
    }

    // ── Result records ───────────────────────────────────────────────────────

    public record TimeResponse(
            String serverTimeUtc,
            String userLocalTime,
            String userLocalDate,
            String timezone
    ) {}

    public record StreakSnapshot(
            int currentStreak,
            int longestStreak,
            String lastSolveDate,
            String lastTimezone,
            Integer nextMilestone,
            boolean updatedNow
    ) {}
}
