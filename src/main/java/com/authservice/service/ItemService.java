package com.authservice.service;

import com.authservice.api.dto.ItemsDto.*;
import com.authservice.domain.items.ItemCatalogEntry;
import com.authservice.domain.items.ItemRepository;
import com.authservice.domain.items.UserItem;
import com.authservice.exception.AppException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Business rules for the item inventory system (ONE-38).
 *
 * The service centralizes:
 *  - {@code grant()} — atomic add with max-stack enforcement and acquisition
 *    logging (used by the admin/test endpoint, future shop, future achievements);
 *  - {@code consumeAuto()} — atomic consume of one unit with consumption logging
 *    (used by the streak protector hook in {@code StreakService.registerSolve});
 *  - read helpers for the {@code GET /items/*} endpoints and for the streak
 *    surfaces that need to expose the protector count.
 */
@Service
public class ItemService {

    private static final Logger log = LoggerFactory.getLogger(ItemService.class);

    /** Catalog id for the streak protector — referenced by StreakService. */
    public static final String STREAK_PROTECTOR_ID = "streak_protector";

    static final String CTX_AUTO_STREAK = "AUTO_STREAK_PROTECTOR";

    private final ItemRepository repo;

    public ItemService(ItemRepository repo) {
        this.repo = repo;
    }

    // ── Catalog / inventory reads ────────────────────────────────────────

    public CatalogResponse getCatalog() {
        List<ItemCatalogEntry> all = repo.findAllCatalog();
        List<CatalogItem> items = all.stream().map(CatalogItem::of).toList();
        return new CatalogResponse(items, items.size());
    }

    public MeResponse getMe(Long userId) {
        List<InventoryItem> inventory = repo.findInventory(userId).stream()
                .map(InventoryItem::of)
                .toList();
        List<UnseenConsumption> unseen = repo.findUnseenConsumptions(userId).stream()
                .map(UnseenConsumption::of)
                .toList();
        return new MeResponse(inventory, unseen, unseen.size());
    }

    /**
     * Returns the user's current quantity of the streak protector, used by
     * {@code StreakService} to decide whether the streak is shielded on
     * read and how many days a registerSolve gap can cover.
     */
    public int getStreakProtectorCount(Long userId) {
        return repo.findQuantity(userId, STREAK_PROTECTOR_ID);
    }

    // ── Notifications ────────────────────────────────────────────────────

    public SeenResponse markAllConsumptionsSeen(Long userId) {
        return new SeenResponse(repo.markAllConsumptionsSeen(userId));
    }

    // ── Grant (admin / test / future shop) ───────────────────────────────

    /**
     * Adds {@code quantity} units of {@code itemId} to the user's inventory,
     * enforcing the catalog's {@code max_stack} and {@code enabled} flags.
     * Logs the acquisition with the recorded {@code source} for trazability.
     *
     * Throws:
     *   - 400 if the item doesn't exist, is disabled, or quantity is invalid;
     *   - 409 if the new quantity would exceed {@code max_stack} (existing
     *     quantity is left untouched).
     */
    @Transactional
    public GrantResponse grant(Long userId, String itemId, int quantity,
                                String source, String sourceRef) {
        if (quantity <= 0) {
            throw new AppException(HttpStatus.BAD_REQUEST, "quantity must be positive");
        }
        ItemCatalogEntry catalog = repo.findCatalog(itemId)
                .orElseThrow(() -> new AppException(HttpStatus.BAD_REQUEST,
                        "Item not found in catalog: " + itemId));
        if (!catalog.enabled) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Item is currently disabled: " + itemId);
        }
        int current = repo.findQuantity(userId, itemId);
        if (current + quantity > catalog.maxStack) {
            throw new AppException(HttpStatus.CONFLICT,
                    "Acquisition would exceed max stack of " + catalog.maxStack +
                    " (current: " + current + ", attempted: +" + quantity + ")");
        }
        int newQ = repo.addQuantity(userId, itemId, quantity);
        repo.logAcquisition(userId, itemId, quantity, source, sourceRef);
        log.info("[ITEMS] grant userId={} item={} qty=+{} source={} → {}",
                userId, itemId, quantity, source, newQ);
        return new GrantResponse(itemId, newQ, catalog.maxStack);
    }

    // ── Auto-consume (used by streak protector hook) ────────────────────

    /**
     * Consumes {@code count} units of the streak protector for the user,
     * logging one row per unit with the corresponding {@code dayCovered}.
     * Caller (StreakService) must have already confirmed the user has at
     * least {@code count} units; this method otherwise throws 500.
     *
     * @param missedDays exactly {@code count} dates, in chronological order,
     *                   that this consumption protected. Each becomes a row
     *                   in {@code item_consumptions} so the client can show
     *                   "we used your protector for day X".
     */
    @Transactional
    public void consumeStreakProtectors(Long userId, List<LocalDate> missedDays) {
        if (missedDays == null || missedDays.isEmpty()) return;
        int count = missedDays.size();
        int current = repo.findQuantity(userId, STREAK_PROTECTOR_ID);
        if (current < count) {
            // Shouldn't happen — StreakService is supposed to clamp count to
            // the available quantity. Bail loudly so we notice.
            throw new IllegalStateException(
                    "Not enough streak protectors: have=" + current + " requested=" + count);
        }
        repo.subtractQuantity(userId, STREAK_PROTECTOR_ID, count);
        for (LocalDate day : missedDays) {
            repo.logConsumption(userId, STREAK_PROTECTOR_ID, day, CTX_AUTO_STREAK);
        }
        log.info("[ITEMS] auto-consume streak_protector userId={} count={} days={}",
                userId, count, missedDays);
    }
}
