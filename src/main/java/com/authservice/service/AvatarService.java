package com.authservice.service;

import com.authservice.api.dto.AvatarsDto.*;
import com.authservice.domain.avatars.AvatarCatalogEntry;
import com.authservice.domain.avatars.AvatarRepository;
import com.authservice.domain.avatars.UserAvatar;
import com.authservice.domain.avatars.UserAvatarState;
import com.authservice.exception.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Avatar system business rules (Tarea 14 / ONE-14).
 *
 * The service enforces every contract from the spec:
 *  - onboarding is one-shot per user (rejected if inventory is non-empty);
 *  - the equipped avatar is free to change but must be in the inventory;
 *  - initial-free changes are capped at 2 lifetime, gated by 24h then 7d
 *    windows, and validated against the catalog flag at attempt time;
 *  - the swap is transactional so a failure mid-way leaves nothing changed.
 */
@Service
public class AvatarService {

    /** First initial-free change window: at least 24 hours after onboarding. */
    static final Duration FIRST_CHANGE_WINDOW = Duration.ofHours(24);

    /** Second initial-free change window: at least 7 days after the first change. */
    static final Duration SECOND_CHANGE_WINDOW = Duration.ofDays(7);

    static final int MAX_INITIAL_CHANGES = 2;

    static final String SOURCE_INITIAL_FREE = "INITIAL_FREE";

    /** Default locale fallback when the client doesn't send Accept-Language. */
    static final String DEFAULT_LOCALE = "es";

    private final AvatarRepository repo;
    private final SocialServiceClient socialServiceClient;

    public AvatarService(AvatarRepository repo, SocialServiceClient socialServiceClient) {
        this.repo = repo;
        this.socialServiceClient = socialServiceClient;
    }

    // ── Catalog / discovery ──────────────────────────────────────────────

    public CatalogResponse getCatalog(String locale) {
        List<AvatarCatalogEntry> all = repo.findAllCatalog(safeLocale(locale));
        List<CatalogItem> items = all.stream().map(CatalogItem::of).toList();
        return new CatalogResponse(items, items.size());
    }

    public CatalogResponse getOnboardingOptions(String locale) {
        List<AvatarCatalogEntry> all = repo.findInitialFreeCatalog(safeLocale(locale));
        List<CatalogItem> items = all.stream().map(CatalogItem::of).toList();
        return new CatalogResponse(items, items.size());
    }

    // ── Per-user snapshot ────────────────────────────────────────────────

    public MeResponse getMe(Long userId, String locale) {
        String loc = safeLocale(locale);
        List<UserAvatar> inventory = repo.findInventory(userId, loc);
        List<InventoryItem> items = inventory.stream().map(InventoryItem::of).toList();

        Optional<UserAvatarState> stateOpt = repo.findState(userId);
        InventoryItem equipped = null;
        if (stateOpt.isPresent() && stateOpt.get().equippedAvatarId != null) {
            String equippedId = stateOpt.get().equippedAvatarId;
            equipped = inventory.stream()
                    .filter(u -> equippedId.equals(u.avatarId))
                    .findFirst()
                    .map(InventoryItem::of)
                    .orElse(null);
        }

        InitialChangeStatus status = computeChangeStatus(stateOpt.orElse(null));
        boolean mustOnboard = items.isEmpty();
        return new MeResponse(mustOnboard, items, equipped, status);
    }

    // ── Onboarding ───────────────────────────────────────────────────────

    /**
     * One-shot onboarding pick. Inserts the chosen avatar into the inventory
     * with source INITIAL_FREE, equips it, seeds the change-window anchor.
     * Rejects if the user already has any avatar (idempotency for the flow
     * is "you only onboard once") or if the avatar isn't currently flagged
     * as initial-free in the catalog.
     */
    @Transactional
    public MeResponse pickInitial(Long userId, String avatarId, String locale) {
        if (avatarId == null || avatarId.isBlank()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "avatarId is required");
        }
        if (repo.countInventory(userId) > 0) {
            throw new AppException(HttpStatus.CONFLICT,
                    "User already has avatars; onboarding is a one-shot operation");
        }
        String loc = safeLocale(locale);
        AvatarCatalogEntry catalog = repo.findCatalogById(avatarId, loc)
                .orElseThrow(() -> new AppException(HttpStatus.BAD_REQUEST,
                        "Avatar not found in catalog: " + avatarId));
        if (!catalog.isInitialFree) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Avatar is not currently flagged as initial-free");
        }

        repo.insertInventory(userId, avatarId, SOURCE_INITIAL_FREE);
        repo.insertOnboardingState(userId, avatarId);

        socialServiceClient.notifyAvatarChanged(userId, avatarId, catalog.imageUrl, catalog.name);

        return getMe(userId, loc);
    }

    // ── Equip ────────────────────────────────────────────────────────────

    /**
     * Equips an avatar the user already owns. Free operation — no counters,
     * no windows. Validates membership in the inventory.
     */
    @Transactional
    public MeResponse equip(Long userId, String avatarId, String locale) {
        if (avatarId == null || avatarId.isBlank()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "avatarId is required");
        }
        String loc = safeLocale(locale);
        UserAvatar entry = repo.findInventoryEntry(userId, avatarId, loc)
                .orElseThrow(() -> new AppException(HttpStatus.BAD_REQUEST,
                        "Avatar is not in user's inventory"));
        repo.updateEquipped(userId, avatarId);

        AvatarCatalogEntry c = entry.catalog;
        socialServiceClient.notifyAvatarChanged(userId, avatarId,
                c == null ? null : c.imageUrl, c == null ? null : c.name);

        return getMe(userId, loc);
    }

    // ── Initial-free change options ──────────────────────────────────────

    public InitialChangeOptionsResponse getInitialChangeOptions(Long userId, String locale) {
        String loc = safeLocale(locale);
        UserAvatarState state = repo.findState(userId).orElse(null);
        InitialChangeStatus status = computeChangeStatus(state);

        List<AvatarCatalogEntry> options = repo.findInitialChangeOptions(userId, loc);
        List<CatalogItem> items = options.stream().map(CatalogItem::of).toList();

        // Surface "user already owns every initial-free avatar" distinctly so the
        // client can show a tailored empty state instead of a generic "no options".
        String emptyReason = items.isEmpty() ? "ALL_OWNED" : null;
        return new InitialChangeOptionsResponse(status, items, items.size(), emptyReason);
    }

    // ── Initial-free change ──────────────────────────────────────────────

    /**
     * Replaces the user's INITIAL_FREE inventory entry with a different
     * initial-free catalog avatar. Validates: changes-remaining, time
     * window, candidate flagged as initial-free, candidate not already in
     * inventory. If the swapped-out avatar was equipped, the new one
     * inherits the equipped slot; otherwise the existing equipped stays.
     * Atomic — failure rolls back inventory + counter.
     */
    @Transactional
    public MeResponse initialChange(Long userId, String newAvatarId, String locale) {
        if (newAvatarId == null || newAvatarId.isBlank()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "newAvatarId is required");
        }
        String loc = safeLocale(locale);

        UserAvatarState state = repo.findState(userId)
                .orElseThrow(() -> new AppException(HttpStatus.CONFLICT,
                        "User has not onboarded — no initial-free to change"));

        UserAvatar current = repo.findInitialFreeEntry(userId, loc)
                .orElseThrow(() -> new AppException(HttpStatus.CONFLICT,
                        "User has no INITIAL_FREE avatar to swap"));

        // Validate window + counter.
        InitialChangeStatus status = computeChangeStatus(state);
        if (status.changesRemaining() == 0) {
            throw new AppException(HttpStatus.CONFLICT,
                    "No initial-free changes remaining");
        }
        if (!status.isOpen()) {
            throw new AppException(HttpStatus.CONFLICT,
                    "Initial-free change window is not open yet");
        }

        // Validate replacement.
        AvatarCatalogEntry candidate = repo.findCatalogById(newAvatarId, loc)
                .orElseThrow(() -> new AppException(HttpStatus.BAD_REQUEST,
                        "Avatar not found in catalog: " + newAvatarId));
        if (!candidate.isInitialFree) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Candidate avatar is not currently flagged as initial-free");
        }
        if (newAvatarId.equals(current.avatarId)) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Candidate is the same as the current initial-free avatar");
        }
        if (repo.hasAvatar(userId, newAvatarId)) {
            throw new AppException(HttpStatus.CONFLICT,
                    "User already owns that avatar");
        }

        boolean replaceEquipped = current.avatarId.equals(state.equippedAvatarId);

        repo.deleteInventory(userId, current.avatarId);
        repo.insertInventory(userId, newAvatarId, SOURCE_INITIAL_FREE);
        repo.recordInitialFreeChange(userId, newAvatarId, replaceEquipped);

        socialServiceClient.notifyAvatarChanged(userId,
                replaceEquipped ? newAvatarId : state.equippedAvatarId,
                replaceEquipped ? candidate.imageUrl : null,
                replaceEquipped ? candidate.name : null);

        return getMe(userId, loc);
    }

    // ── Cross-surface enrichment helper ──────────────────────────────────

    /**
     * Used by InternalUserController to populate the {@code avatarUrl} field
     * of {@code InternalUserDto}. Returns null when the user has no avatar
     * equipped (which only happens before onboarding).
     */
    public String getEquippedImageUrl(Long userId) {
        return repo.findEquippedImageUrl(userId).orElse(null);
    }

    // ── Internal: window / counter computation ───────────────────────────

    /**
     * Computes {@link InitialChangeStatus} from the current state row. The
     * rules:
     *  - if no state (user hasn't onboarded), status is open with 2
     *    remaining (formally meaningless until they pick, but the client
     *    benefits from the consistent shape);
     *  - 1st change unlocks 24h after {@code initial_free_acquired_at};
     *  - 2nd change unlocks 7d after {@code last_initial_free_change_at};
     *  - after 2 changes used, status reports 0 remaining and never opens.
     */
    /**
     * Normalizes Accept-Language header values to the base ISO 639-1 code
     * stored in the i18n table. Accepts {@code "es-MX"}, {@code "es-MX,en;q=0.9"},
     * {@code "es"}, etc.; falls back to {@link #DEFAULT_LOCALE} on null/blank.
     */
    static String safeLocale(String header) {
        if (header == null || header.isBlank()) return DEFAULT_LOCALE;
        // Accept-Language can be a full RFC 7231 list; we only need the
        // first language tag's primary subtag.
        String first = header.split(",", 2)[0].trim();
        // Strip quality suffix if present (e.g. "es-MX;q=0.9").
        int semi = first.indexOf(';');
        if (semi >= 0) first = first.substring(0, semi).trim();
        // Lowercase + base subtag only ("es-MX" → "es", "ZH-Hant" → "zh").
        int dash = first.indexOf('-');
        String base = (dash >= 0 ? first.substring(0, dash) : first).toLowerCase();
        return base.isEmpty() ? DEFAULT_LOCALE : base;
    }

    private InitialChangeStatus computeChangeStatus(UserAvatarState state) {
        if (state == null || state.initialFreeAcquiredAt == null) {
            // User hasn't onboarded yet. Surfaces a fully-open state shape so
            // the client can call /avatars/me before picking and not get a
            // weird null-status payload.
            return new InitialChangeStatus(MAX_INITIAL_CHANGES, 0, null, true, "OPEN");
        }
        int used = state.initialFreeChangesUsed;
        int remaining = Math.max(0, MAX_INITIAL_CHANGES - used);

        if (remaining == 0) {
            return new InitialChangeStatus(0, used, null, false, "NO_CHANGES_LEFT");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime windowAnchor = used == 0
                ? state.initialFreeAcquiredAt.plus(FIRST_CHANGE_WINDOW)
                : (state.lastInitialFreeChangeAt == null
                    // Defensive: counter advanced without timestamp — open now.
                    ? now
                    : state.lastInitialFreeChangeAt.plus(SECOND_CHANGE_WINDOW));
        boolean open = !now.isBefore(windowAnchor);
        return new InitialChangeStatus(
                remaining, used,
                open ? null : windowAnchor,
                open,
                open ? "OPEN" : "WINDOW_NOT_OPEN");
    }
}
