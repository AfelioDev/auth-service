package com.authservice.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * POJOs for the user-data sync feature (Tarea 59 / ONE-27).
 * Bundled in one file because each is a small, framework-free record.
 */
public final class UserData {

    private UserData() {}

    public static final class Solve {
        public Long id;
        public Long userId;
        public String clientId;
        public String sessionClientId;
        public Integer solveTimeMs;
        public OffsetDateTime solvedAt;
        public String scramble;
        public String scrambleImageSvg;
        public Boolean isOk;
        public Boolean hasPlus2;
        public Boolean hasDnf;
        public Boolean isFavorite;
        public Boolean isDeleted;
        public String comments;
        public OffsetDateTime createdAt;
        public OffsetDateTime updatedAt;
    }

    public static final class Session {
        public Long id;
        public Long userId;
        public String clientId;
        public String name;
        public String categoryId;
        public OffsetDateTime createdAt;
        public OffsetDateTime updatedAt;
    }

    public static final class Preferences {
        public Long userId;
        public Map<String, Object> preferences;
        public OffsetDateTime updatedAt;
    }

    public static final class Profile {
        public Long userId;
        public String displayName;
        public OffsetDateTime displayNameUpdatedAt;
        public OffsetDateTime updatedAt;
    }

    public static final class LearnedAlgorithm {
        public Long id;
        public Long userId;
        public String caseId;
        public String submethodId;
        public OffsetDateTime learnedAt;
    }

    public static final class Avatar {
        public Long id;
        public Long userId;
        public String avatarId;
        public String name;
        public String description;
        public Integer rarity;
        public OffsetDateTime unlockedAt;
    }

    /**
     * One synced solve flattened with its session's category. Used by the
     * in-app records endpoint — we don't expose this shape to clients,
     * just the aggregated `bestSingle` / `bestAo5` per category.
     */
    public record RecordSolve(
            String eventId,
            String sessionClientId,
            int timeMs,
            boolean hasPlus2,
            boolean hasDnf,
            OffsetDateTime solvedAt,
            String scramble
    ) {
        /** Effective time for ranking: raw time + 200ms penalty if hasPlus2; DNF = max. */
        public long effectiveTimeMs() {
            if (hasDnf) return Long.MAX_VALUE;
            return (long) timeMs + (hasPlus2 ? 200L : 0L);
        }
    }

    /** Aggregated read snapshot returned by GET /user-data/snapshot. */
    public static final class Snapshot {
        public List<Solve> solves;
        public List<Session> sessions;
        public Preferences preferences;
        public Profile profile;
        public List<LearnedAlgorithm> learn;
        public List<Avatar> avatars;
    }
}
