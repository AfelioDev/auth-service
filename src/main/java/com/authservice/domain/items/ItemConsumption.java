package com.authservice.domain.items;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * One entry in the consumption log (ONE-38). For the streak protector,
 * {@code dayCovered} is the missed day that the consumption protected;
 * {@code contextRef} typically reads {@code AUTO_STREAK_PROTECTOR}.
 * {@code seenByUserAt} tracks whether the user has been shown the
 * "we used your protector for day X" notice.
 */
public final class ItemConsumption {
    public Long id;
    public Long userId;
    public String itemId;
    public OffsetDateTime consumedAt;
    public LocalDate dayCovered;
    public String contextRef;
    public OffsetDateTime seenByUserAt;
}
