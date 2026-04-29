package com.authservice.domain.avatars;

import java.time.OffsetDateTime;

/**
 * Per-user mutable state for the avatar system (Tarea 14 / ONE-14):
 * which avatar is equipped, when the initial-free was first picked, and how
 * many of the two lifetime initial-free changes have been used (and when).
 *
 * Created lazily on the first onboarding pick. Until then the user has
 * an empty inventory and no row here — the service layer treats that as
 * {@code mustOnboard = true}.
 */
public final class UserAvatarState {
    public Long userId;
    public String equippedAvatarId;
    public OffsetDateTime initialFreeAcquiredAt;
    public int initialFreeChangesUsed;
    public OffsetDateTime lastInitialFreeChangeAt;
    public OffsetDateTime updatedAt;
}
