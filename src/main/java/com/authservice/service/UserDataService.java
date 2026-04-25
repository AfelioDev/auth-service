package com.authservice.service;

import com.authservice.domain.UserData.*;
import com.authservice.domain.UserDataRepository;
import com.authservice.exception.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.Set;

/**
 * User-data sync orchestration (Tarea 59 / ONE-27).
 *
 * Thin wrapper over UserDataRepository plus the cross-cutting concerns:
 *  - displayName validation against the user's WCA name (Tarea 56)
 *  - transactional snapshot upload (replaces the user's whole cloud state
 *    in a single tx so partial failures never leave the user in a torn state)
 */
@Service
public class UserDataService {

    private final UserDataRepository repo;
    private final SocialServiceClient socialServiceClient;

    public UserDataService(UserDataRepository repo, SocialServiceClient socialServiceClient) {
        this.repo = repo;
        this.socialServiceClient = socialServiceClient;
    }

    // ── Solves ────────────────────────────────────────────────────────────

    public List<Solve> getSolves(Long userId, OffsetDateTime since, int limit) {
        int cap = Math.min(Math.max(limit, 1), 1000);
        return repo.findSolvesByUser(userId, since, cap);
    }

    public int upsertSolves(Long userId, List<Solve> solves) {
        if (solves == null || solves.isEmpty()) return 0;
        for (Solve s : solves) {
            if (s.clientId == null || s.clientId.isBlank()) {
                throw new AppException(HttpStatus.BAD_REQUEST, "solves[].clientId is required");
            }
            if (s.solveTimeMs == null) {
                throw new AppException(HttpStatus.BAD_REQUEST, "solves[].solveTimeMs is required");
            }
        }
        return repo.upsertSolves(userId, solves);
    }

    public boolean softDeleteSolve(Long userId, String clientId) {
        return repo.softDeleteSolve(userId, clientId);
    }

    // ── Sessions ──────────────────────────────────────────────────────────

    public List<Session> getSessions(Long userId) {
        return repo.findSessionsByUser(userId);
    }

    public int upsertSessions(Long userId, List<Session> sessions) {
        if (sessions == null || sessions.isEmpty()) return 0;
        for (Session s : sessions) {
            if (s.clientId == null || s.clientId.isBlank()) {
                throw new AppException(HttpStatus.BAD_REQUEST, "sessions[].clientId is required");
            }
            if (s.name == null || s.name.isBlank()) {
                throw new AppException(HttpStatus.BAD_REQUEST, "sessions[].name is required");
            }
            if (s.categoryId == null || s.categoryId.isBlank()) {
                throw new AppException(HttpStatus.BAD_REQUEST, "sessions[].categoryId is required");
            }
        }
        return repo.upsertSessions(userId, sessions);
    }

    public boolean deleteSession(Long userId, String clientId) {
        return repo.deleteSession(userId, clientId);
    }

    // ── Preferences ───────────────────────────────────────────────────────

    public Preferences getPreferences(Long userId) {
        Preferences p = repo.findPreferences(userId);
        if (p == null) {
            // Lazy-create an empty row so the client always gets a 200 with a stable shape.
            return repo.upsertPreferences(userId, Map.of(), true);
        }
        return p;
    }

    public Preferences putPreferences(Long userId, Map<String, Object> prefs) {
        return repo.upsertPreferences(userId, prefs == null ? Map.of() : prefs, true);
    }

    public Preferences patchPreferences(Long userId, Map<String, Object> prefs) {
        return repo.upsertPreferences(userId, prefs == null ? Map.of() : prefs, false);
    }

    // ── Profile ───────────────────────────────────────────────────────────

    public Profile getProfile(Long userId) {
        Profile p = repo.findProfile(userId);
        if (p == null) {
            // Create an empty row so the contract is stable.
            return repo.upsertProfile(userId, null);
        }
        return p;
    }

    /**
     * Updates the display name. If non-null, validates that it is a valid
     * subset of the user's WCA name (≥ 2 tokens, all tokens drawn from the
     * original). Pass null to clear the override and fall back to the WCA name.
     */
    public Profile putProfile(Long userId, String displayName) {
        if (displayName != null) {
            String trimmed = displayName.trim();
            if (trimmed.isEmpty()) {
                displayName = null;
            } else {
                String wcaName = repo.findUserWcaName(userId)
                        .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "user has no WCA name on file"));
                if (!isValidDisplayName(trimmed, wcaName)) {
                    throw new AppException(HttpStatus.BAD_REQUEST,
                            "displayName must be a subset of the user's WCA name with at least 2 tokens");
                }
                displayName = trimmed;
            }
        }
        return repo.upsertProfile(userId, displayName);
    }

    /**
     * Generates all valid display name combinations from a user's WCA name.
     * Each combination has >= 2 tokens, all drawn from the original name.
     * Returns the options using the ORIGINAL casing (not the normalized form),
     * so the picker shows "Edgar Cortes", not "edgar cortes".
     */
    public List<String> getDisplayNameOptions(Long userId) {
        String wcaName = repo.findUserWcaName(userId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "user has no WCA name on file"));
        String[] originalTokens = wcaName.trim().split("\\s+");
        if (originalTokens.length < 2) return List.of(wcaName.trim());

        List<String> options = new ArrayList<>();
        // Generate all subsets of size >= 2, preserving original order.
        int n = originalTokens.length;
        for (int mask = 0; mask < (1 << n); mask++) {
            int bits = Integer.bitCount(mask);
            if (bits < 2) continue;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < n; i++) {
                if ((mask & (1 << i)) != 0) {
                    if (sb.length() > 0) sb.append(' ');
                    sb.append(originalTokens[i]);
                }
            }
            options.add(sb.toString());
        }
        // Sort: shortest first, then alphabetical. Full name last.
        options.sort(Comparator.comparingInt((String s) -> s.split("\\s+").length)
                .thenComparing(Comparator.naturalOrder()));
        return options;
    }

    /**
     * Validates that every token of the candidate is present in the WCA name
     * (case- and accent-insensitive) and that the candidate has at least 2 tokens.
     * Order doesn't matter; uniqueness is enforced (the candidate cannot use the
     * same WCA token twice).
     */
    static boolean isValidDisplayName(String candidate, String wcaName) {
        if (wcaName == null) return false;
        String[] candTokens = tokenize(candidate);
        if (candTokens.length < 2) return false;

        Set<String> wcaTokens = new HashSet<>(Arrays.asList(tokenize(wcaName)));
        for (String t : candTokens) {
            if (!wcaTokens.contains(t)) return false;
        }
        return true;
    }

    /**
     * Updates the display name with rate limiting: rejects if the last change
     * was less than 30 days ago (unless displayName was never set before).
     *
     * On success, fires a DISPLAY_NAME_CHANGED WS event (via social-service) so
     * the user's own clients and every friend's open client see the new name
     * immediately, without waiting for a refresh.
     */
    public Profile putProfileWithRateLimit(Long userId, String displayName) {
        if (displayName != null) {
            Profile existing = repo.findProfile(userId);
            if (existing != null && existing.displayName != null
                    && existing.displayNameUpdatedAt != null) {
                long daysSinceLastChange = java.time.temporal.ChronoUnit.DAYS.between(
                        existing.displayNameUpdatedAt.toLocalDate(),
                        java.time.LocalDate.now(java.time.ZoneOffset.UTC));
                if (daysSinceLastChange < 30) {
                    throw new AppException(HttpStatus.TOO_MANY_REQUESTS,
                            "Display name can only be changed once every 30 days. Next change available in "
                            + (30 - daysSinceLastChange) + " days.");
                }
            }
        }
        Profile result = putProfile(userId, displayName);
        // Resolve the value clients should see going forward: the override if set,
        // else the WCA / registration name. This is what we broadcast.
        String broadcastName = result.displayName != null && !result.displayName.isBlank()
                ? result.displayName
                : repo.findUserWcaName(userId).orElse(null);
        socialServiceClient.notifyDisplayNameChanged(userId, broadcastName);
        return result;
    }

    private static String[] tokenize(String s) {
        if (s == null) return new String[0];
        String norm = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")  // strip accents
                .toLowerCase()
                .trim();
        if (norm.isEmpty()) return new String[0];
        return norm.split("\\s+");
    }

    // ── Learn ─────────────────────────────────────────────────────────────

    public List<LearnedAlgorithm> getLearn(Long userId) {
        return repo.findLearnByUser(userId);
    }

    public boolean addLearn(Long userId, String caseId, String submethodId) {
        if (caseId == null || submethodId == null) {
            throw new AppException(HttpStatus.BAD_REQUEST, "caseId and submethodId are required");
        }
        return repo.addLearn(userId, caseId, submethodId);
    }

    public boolean removeLearn(Long userId, String caseId, String submethodId) {
        if (caseId == null || submethodId == null) {
            throw new AppException(HttpStatus.BAD_REQUEST, "caseId and submethodId are required");
        }
        return repo.removeLearn(userId, caseId, submethodId);
    }

    // ── Avatars ───────────────────────────────────────────────────────────

    public List<Avatar> getAvatars(Long userId) {
        return repo.findAvatarsByUser(userId);
    }

    public boolean addAvatar(Long userId, Avatar a) {
        if (a == null || a.avatarId == null) {
            throw new AppException(HttpStatus.BAD_REQUEST, "avatarId is required");
        }
        return repo.addAvatar(userId, a);
    }

    // ── Snapshot ──────────────────────────────────────────────────────────

    public Snapshot getSnapshot(Long userId) {
        Snapshot snap = new Snapshot();
        snap.solves = repo.findSolvesByUser(userId, null, 100_000);
        snap.sessions = repo.findSessionsByUser(userId);
        snap.preferences = repo.findPreferences(userId);
        snap.profile = repo.findProfile(userId);
        snap.learn = repo.findLearnByUser(userId);
        snap.avatars = repo.findAvatarsByUser(userId);
        return snap;
    }

    /**
     * Replaces the user's entire cloud state with the contents of the snapshot,
     * atomically. Used by the "upload my local data" flow at first login.
     *
     * Display name validation runs INSIDE the transaction so a bad payload
     * rolls back everything.
     */
    @Transactional
    public Snapshot putSnapshot(Long userId, Snapshot snap) {
        if (snap == null) snap = new Snapshot();

        // Wipe everything first.
        repo.deleteAllSolves(userId);
        repo.deleteAllSessions(userId);
        repo.deletePreferences(userId);
        repo.deleteProfile(userId);
        repo.deleteAllLearn(userId);
        repo.deleteAllAvatars(userId);

        // Re-insert. Sessions must come before solves (FK by string clientId).
        if (snap.sessions != null && !snap.sessions.isEmpty()) {
            upsertSessions(userId, snap.sessions);
        }
        if (snap.solves != null && !snap.solves.isEmpty()) {
            upsertSolves(userId, snap.solves);
        }
        if (snap.preferences != null && snap.preferences.preferences != null) {
            repo.upsertPreferences(userId, snap.preferences.preferences, true);
        }
        if (snap.profile != null) {
            // putProfile validates; if invalid, the @Transactional rolls everything back.
            putProfile(userId, snap.profile.displayName);
        }
        if (snap.learn != null) {
            for (LearnedAlgorithm l : snap.learn) {
                repo.addLearn(userId, l.caseId, l.submethodId);
            }
        }
        if (snap.avatars != null) {
            for (Avatar a : snap.avatars) {
                repo.addAvatar(userId, a);
            }
        }

        return getSnapshot(userId);
    }
}
