package com.authservice.api;

import com.authservice.api.dto.ItemsDto.*;
import com.authservice.exception.AppException;
import com.authservice.service.ItemService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Internal item endpoints (ONE-38). Admin/test entry point for seeding
 * items into a user's inventory; reachable only from inside the docker
 * network (the {@code /internal/**} prefix is not exposed via Traefik).
 *
 * Exists separately from {@link InternalUserController} because items are
 * a distinct subsystem and the interface here will grow as future
 * acquisition vehicles (shop, achievements) come online.
 */
@RestController
@RequestMapping("/internal/items")
public class InternalItemsController {

    private static final String SOURCE_ADMIN_GRANT = "ADMIN_GRANT";

    private final ItemService itemService;

    public InternalItemsController(ItemService itemService) {
        this.itemService = itemService;
    }

    /**
     * Grants {@code body.quantity} units of {@code body.itemId} to the user
     * identified by {@code body.userId}. Source is recorded as
     * {@code ADMIN_GRANT} for audit. Rejects with 409 if the result would
     * exceed the catalog's {@code max_stack}; existing inventory is not
     * touched in that case.
     */
    @PostMapping("/grant")
    public ResponseEntity<GrantResponse> grant(@RequestBody GrantRequest body) {
        if (body == null || body.userId() == null || body.itemId() == null
                || body.quantity() == null) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "userId, itemId, quantity are required");
        }
        return ResponseEntity.ok(itemService.grant(
                body.userId(),
                body.itemId(),
                body.quantity(),
                SOURCE_ADMIN_GRANT,
                body.sourceRef()));
    }
}
