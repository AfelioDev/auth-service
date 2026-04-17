package com.authservice.api;

import com.authservice.domain.UserData.*;
import com.authservice.service.UserDataService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * All `/user-data/*` endpoints (Tarea 59 / ONE-27).
 * One controller for the six entities + the snapshot bulk endpoints.
 *
 * Every endpoint requires a JWT — the user_id is derived from the token,
 * never from the request body or path, so users can never read or write
 * each other's data.
 */
@RestController
@RequestMapping("/user-data")
public class UserDataController {

    private final UserDataService service;

    public UserDataController(UserDataService service) {
        this.service = service;
    }

    private Long currentUserId(Authentication auth) {
        return Long.parseLong((String) auth.getPrincipal());
    }

    // ── Solves ────────────────────────────────────────────────────────────

    @GetMapping("/solves")
    public ResponseEntity<Map<String, Object>> getSolves(
            Authentication auth,
            @RequestParam(name = "since", required = false) String since,
            @RequestParam(name = "limit", required = false, defaultValue = "500") int limit) {
        OffsetDateTime sinceTs = null;
        if (since != null && !since.isBlank()) {
            try {
                sinceTs = OffsetDateTime.parse(since);
            } catch (Exception ignored) { /* treat invalid as null */ }
        }
        List<Solve> rows = service.getSolves(currentUserId(auth), sinceTs, limit);
        return ResponseEntity.ok(Map.of("solves", rows, "count", rows.size()));
    }

    @PostMapping("/solves")
    public ResponseEntity<Map<String, Object>> upsertSolves(
            Authentication auth,
            @RequestBody SolvesUpsertBody body) {
        int n = service.upsertSolves(currentUserId(auth), body == null ? null : body.solves);
        return ResponseEntity.ok(Map.of("upserted", n));
    }

    @DeleteMapping("/solves/{clientId}")
    public ResponseEntity<Void> deleteSolve(Authentication auth, @PathVariable String clientId) {
        boolean ok = service.softDeleteSolve(currentUserId(auth), clientId);
        return ok ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    public static final class SolvesUpsertBody {
        public List<Solve> solves;
    }

    // ── Sessions ──────────────────────────────────────────────────────────

    @GetMapping("/sessions")
    public ResponseEntity<Map<String, Object>> getSessions(Authentication auth) {
        List<Session> rows = service.getSessions(currentUserId(auth));
        return ResponseEntity.ok(Map.of("sessions", rows, "count", rows.size()));
    }

    @PostMapping("/sessions")
    public ResponseEntity<Map<String, Object>> upsertSessions(
            Authentication auth,
            @RequestBody SessionsUpsertBody body) {
        int n = service.upsertSessions(currentUserId(auth), body == null ? null : body.sessions);
        return ResponseEntity.ok(Map.of("upserted", n));
    }

    @DeleteMapping("/sessions/{clientId}")
    public ResponseEntity<Void> deleteSession(Authentication auth, @PathVariable String clientId) {
        boolean ok = service.deleteSession(currentUserId(auth), clientId);
        return ok ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    public static final class SessionsUpsertBody {
        public List<Session> sessions;
    }

    // ── Preferences ───────────────────────────────────────────────────────

    @GetMapping("/preferences")
    public ResponseEntity<Preferences> getPreferences(Authentication auth) {
        return ResponseEntity.ok(service.getPreferences(currentUserId(auth)));
    }

    @PutMapping("/preferences")
    public ResponseEntity<Preferences> putPreferences(
            Authentication auth, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(service.putPreferences(currentUserId(auth), body));
    }

    @PatchMapping("/preferences")
    public ResponseEntity<Preferences> patchPreferences(
            Authentication auth, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(service.patchPreferences(currentUserId(auth), body));
    }

    // ── Profile ───────────────────────────────────────────────────────────

    @GetMapping("/profile")
    public ResponseEntity<Profile> getProfile(Authentication auth) {
        return ResponseEntity.ok(service.getProfile(currentUserId(auth)));
    }

    @GetMapping("/profile/displayname-options")
    public ResponseEntity<Map<String, Object>> displayNameOptions(Authentication auth) {
        List<String> options = service.getDisplayNameOptions(currentUserId(auth));
        return ResponseEntity.ok(Map.of("options", options, "count", options.size()));
    }

    @PutMapping("/profile")
    public ResponseEntity<Profile> putProfile(
            Authentication auth, @RequestBody ProfileBody body) {
        return ResponseEntity.ok(service.putProfileWithRateLimit(currentUserId(auth),
                body == null ? null : body.displayName));
    }

    public static final class ProfileBody {
        public String displayName;
    }

    // ── Learn ─────────────────────────────────────────────────────────────

    @GetMapping("/learn")
    public ResponseEntity<Map<String, Object>> getLearn(Authentication auth) {
        List<LearnedAlgorithm> rows = service.getLearn(currentUserId(auth));
        return ResponseEntity.ok(Map.of("learn", rows, "count", rows.size()));
    }

    @PostMapping("/learn")
    public ResponseEntity<Map<String, Object>> addLearn(
            Authentication auth, @RequestBody LearnBody body) {
        boolean inserted = service.addLearn(currentUserId(auth), body.caseId, body.submethodId);
        return ResponseEntity.ok(Map.of("inserted", inserted));
    }

    @DeleteMapping("/learn")
    public ResponseEntity<Map<String, Object>> removeLearn(
            Authentication auth, @RequestBody LearnBody body) {
        boolean removed = service.removeLearn(currentUserId(auth), body.caseId, body.submethodId);
        return ResponseEntity.ok(Map.of("removed", removed));
    }

    public static final class LearnBody {
        public String caseId;
        public String submethodId;
    }

    // ── Avatars ───────────────────────────────────────────────────────────

    @GetMapping("/avatars")
    public ResponseEntity<Map<String, Object>> getAvatars(Authentication auth) {
        List<Avatar> rows = service.getAvatars(currentUserId(auth));
        return ResponseEntity.ok(Map.of("avatars", rows, "count", rows.size()));
    }

    @PostMapping("/avatars")
    public ResponseEntity<Map<String, Object>> addAvatar(
            Authentication auth, @RequestBody Avatar body) {
        boolean ok = service.addAvatar(currentUserId(auth), body);
        return ResponseEntity.ok(Map.of("inserted", ok));
    }

    // ── Snapshot ──────────────────────────────────────────────────────────

    @GetMapping("/snapshot")
    public ResponseEntity<Snapshot> getSnapshot(Authentication auth) {
        return ResponseEntity.ok(service.getSnapshot(currentUserId(auth)));
    }

    @PostMapping("/snapshot")
    public ResponseEntity<Snapshot> putSnapshot(
            Authentication auth, @RequestBody Snapshot body) {
        return ResponseEntity.ok(service.putSnapshot(currentUserId(auth), body));
    }
}
