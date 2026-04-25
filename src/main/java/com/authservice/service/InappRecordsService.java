package com.authservice.service;

import com.authservice.api.dto.InappRecordsDto.*;
import com.authservice.domain.UserData.RecordSolve;
import com.authservice.domain.UserDataRepository;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Computes in-app records (best single + best Ao5 per category) for a given
 * user, applying WCA averaging rules:
 *  - Single: minimum effective time among non-DNF solves.
 *  - Ao5: best across all chronological 5-solve windows; in each window
 *    drop best+worst and average the middle 3. DNF counts as worst (one
 *    DNF can be discarded). 2+ DNFs make the window's Ao5 a DNF.
 *
 * Exclusions integrate with ONE-17: if the user has an
 * `inapp_records_exclusions` array in `user_preferences`, any solve from
 * those session client_ids is filtered out at the SQL layer before any
 * computation runs. This contract stays valid even if ONE-17 lands a
 * dedicated table later — only the loadExclusions() reader changes.
 */
@Service
public class InappRecordsService {

    private final UserDataRepository repo;

    public InappRecordsService(UserDataRepository repo) {
        this.repo = repo;
    }

    public Response compute(Long userId) {
        Set<String> exclusions = loadExclusions(userId);
        List<RecordSolve> solves = repo.findRecordSolvesByUser(userId, exclusions);
        if (solves.isEmpty()) return new Response(List.of());

        // Group by event preserving chronological order within each group.
        Map<String, List<RecordSolve>> byEvent = new LinkedHashMap<>();
        for (RecordSolve s : solves) {
            byEvent.computeIfAbsent(s.eventId(), k -> new ArrayList<>()).add(s);
        }

        List<CategoryRecord> records = new ArrayList<>();
        for (Map.Entry<String, List<RecordSolve>> e : byEvent.entrySet()) {
            CategoryRecord rec = computeCategoryRecord(e.getKey(), e.getValue());
            if (rec != null) records.add(rec);
        }

        // Stable sort: events with more solves first feels natural for a profile,
        // but keeps insertion order otherwise. Sort by single ascending so the
        // fastest event surfaces at the top.
        records.sort(Comparator.comparingLong(r -> r.bestSingle().timeMs()));
        return new Response(records);
    }

    private CategoryRecord computeCategoryRecord(String eventId, List<RecordSolve> solves) {
        BestSingle bestSingle = computeBestSingle(solves);
        if (bestSingle == null) return null; // no valid (non-DNF) solves for this event
        BestAo5 bestAo5 = computeBestAo5(solves);
        return new CategoryRecord(eventId, bestSingle, bestAo5);
    }

    private BestSingle computeBestSingle(List<RecordSolve> solves) {
        RecordSolve best = null;
        long bestTime = Long.MAX_VALUE;
        for (RecordSolve s : solves) {
            if (s.hasDnf()) continue;
            long t = s.effectiveTimeMs();
            if (t < bestTime) {
                bestTime = t;
                best = s;
            }
        }
        if (best == null) return null;
        return new BestSingle(bestTime, best.solvedAt(), best.scramble());
    }

    private BestAo5 computeBestAo5(List<RecordSolve> solves) {
        if (solves.size() < 5) return null;
        Long bestAvg = null;
        List<RecordSolve> bestWindow = null;
        for (int i = 0; i + 5 <= solves.size(); i++) {
            List<RecordSolve> window = solves.subList(i, i + 5);
            Long avg = ao5Of(window);
            if (avg != null && (bestAvg == null || avg < bestAvg)) {
                bestAvg = avg;
                bestWindow = window;
            }
        }
        if (bestAvg == null || bestWindow == null) return null;
        List<Ao5Solve> serialized = new ArrayList<>(5);
        for (RecordSolve s : bestWindow) {
            serialized.add(new Ao5Solve(s.timeMs(), s.hasPlus2(), s.hasDnf()));
        }
        return new BestAo5(bestAvg, serialized);
    }

    /** Returns the WCA Ao5 of a 5-solve window, or null if the window is a DNF. */
    private Long ao5Of(List<RecordSolve> window) {
        long[] times = new long[5];
        int dnfs = 0;
        for (int i = 0; i < 5; i++) {
            RecordSolve s = window.get(i);
            if (s.hasDnf()) {
                dnfs++;
                times[i] = Long.MAX_VALUE;
            } else {
                times[i] = s.effectiveTimeMs();
            }
        }
        if (dnfs >= 2) return null;
        // Sort, drop index 0 (best) and index 4 (worst — DNF goes here when present).
        long[] sorted = times.clone();
        Arrays.sort(sorted);
        long sum = sorted[1] + sorted[2] + sorted[3];
        // Round-half-up to nearest ms — matches WCA averaging which truncates to the
        // displayed precision.  We carry ms here; rounding doesn't matter for ordering.
        return Math.round(sum / 3.0);
    }

    /**
     * Reads excluded session client_ids from `user_preferences.preferences ->
     * 'inapp_records_exclusions'`. ONE-17 will populate this; until then the
     * call is a no-op and an empty set is returned, so records are computed
     * over every session.
     */
    @SuppressWarnings("unchecked")
    private Set<String> loadExclusions(Long userId) {
        var prefs = repo.findPreferences(userId);
        if (prefs == null || prefs.preferences == null) return Set.of();
        Object raw = prefs.preferences.get("inapp_records_exclusions");
        if (!(raw instanceof List<?> list) || list.isEmpty()) return Set.of();
        Set<String> out = new HashSet<>();
        for (Object item : list) {
            if (item != null) out.add(item.toString());
        }
        return out;
    }
}
