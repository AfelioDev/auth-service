package com.authservice.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Aggregated in-app records for a single user. Returned by the public
 * `GET /users/{userId}/inapp-records` endpoint. Sessions, exclusions, and
 * raw solves are intentionally not exposed — only the per-category bests.
 */
public final class InappRecordsDto {

    private InappRecordsDto() {}

    public record Response(List<CategoryRecord> records) {}

    /**
     * One entry per category that has at least one valid solve for the user.
     * `bestSingle` is always present in this case; `bestAo5` may be null if
     * the user has fewer than 5 solves in that category or every Ao5 window
     * resolves to a DNF.
     */
    public record CategoryRecord(
            String eventId,
            BestSingle bestSingle,
            @JsonInclude(JsonInclude.Include.NON_NULL) BestAo5 bestAo5
    ) {}

    /**
     * `timeMs` already includes the +2 penalty when applicable — the client
     * just renders it. `solvedAt` lets the profile show "achieved on Apr 12".
     */
    public record BestSingle(
            long timeMs,
            @JsonInclude(JsonInclude.Include.NON_NULL) OffsetDateTime solvedAt,
            @JsonInclude(JsonInclude.Include.NON_NULL) String scramble
    ) {}

    /**
     * `avgMs` is the mean of the middle 3 in the best 5-solve window
     * (best/worst dropped per WCA rules). `solves` lists the 5 solves of
     * that window in chronological order so the UI can highlight the
     * dropped ones if it wants.
     */
    public record BestAo5(
            long avgMs,
            List<Ao5Solve> solves
    ) {}

    public record Ao5Solve(long timeMs, boolean isPlus2, boolean isDnf) {}
}
