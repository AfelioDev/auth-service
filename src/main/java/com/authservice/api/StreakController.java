package com.authservice.api;

import com.authservice.service.StreakService;
import com.authservice.service.StreakService.StreakSnapshot;
import com.authservice.service.StreakService.TimeResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Daily streaks (Tarea 13 / ONE-13) plus the server-time oracle.
 *
 * `/time` is public — used by the client on cold start to know the real
 * date independently of the device clock.
 *
 * `/streaks/me` and `/streaks/register-solve` require the JWT.
 */
@RestController
public class StreakController {

    private final StreakService streakService;

    public StreakController(StreakService streakService) {
        this.streakService = streakService;
    }

    // ── Time oracle (public) ─────────────────────────────────────────────────

    @GetMapping("/time")
    public ResponseEntity<TimeResponse> time(
            @RequestParam(name = "timezone", required = false, defaultValue = "UTC") String timezone) {
        return ResponseEntity.ok(streakService.resolveTime(timezone));
    }

    // ── Streak endpoints (authenticated) ─────────────────────────────────────

    @GetMapping("/streaks/me")
    public ResponseEntity<Map<String, Object>> myStreak(Authentication auth) {
        StreakSnapshot s = streakService.getStreak(currentUserId(auth));
        return ResponseEntity.ok(toJson(s));
    }

    @GetMapping("/streaks/user/{userId}")
    public ResponseEntity<Map<String, Object>> userStreak(
            Authentication auth, @PathVariable Long userId) {
        StreakSnapshot s = streakService.getStreak(userId);
        return ResponseEntity.ok(toJson(s));
    }

    @PostMapping("/streaks/register-solve")
    public ResponseEntity<Map<String, Object>> registerSolve(
            Authentication auth,
            @RequestBody RegisterSolveBody body) {
        String tz = body != null && body.timezone() != null ? body.timezone() : "UTC";
        StreakSnapshot s = streakService.registerSolve(currentUserId(auth), tz);
        return ResponseEntity.ok(toJson(s));
    }

    // ── DTOs / helpers ───────────────────────────────────────────────────────

    public record RegisterSolveBody(String timezone) {}

    private Long currentUserId(Authentication auth) {
        return Long.parseLong((String) auth.getPrincipal());
    }

    /** Stable JSON ordering so the contract is predictable. */
    private Map<String, Object> toJson(StreakSnapshot s) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("currentStreak", s.currentStreak());
        body.put("longestStreak", s.longestStreak());
        body.put("lastSolveDate", s.lastSolveDate());
        body.put("lastTimezone", s.lastTimezone());
        body.put("nextMilestone", s.nextMilestone());
        body.put("updatedNow", s.updatedNow());
        return body;
    }
}
