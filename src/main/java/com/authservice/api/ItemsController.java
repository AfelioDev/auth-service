package com.authservice.api;

import com.authservice.api.dto.ItemsDto.*;
import com.authservice.service.ItemService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Item inventory HTTP API (ONE-38). The catalog is public to authenticated
 * clients; the {@code /items/me/*} routes scope every operation to the JWT
 * principal so users never read or write someone else's inventory.
 */
@RestController
@RequestMapping("/items")
public class ItemsController {

    private final ItemService service;

    public ItemsController(ItemService service) {
        this.service = service;
    }

    private Long currentUserId(Authentication auth) {
        return Long.parseLong((String) auth.getPrincipal());
    }

    @GetMapping("/catalog")
    public ResponseEntity<CatalogResponse> getCatalog() {
        return ResponseEntity.ok(service.getCatalog());
    }

    @GetMapping("/me")
    public ResponseEntity<MeResponse> getMe(Authentication auth) {
        return ResponseEntity.ok(service.getMe(currentUserId(auth)));
    }

    @PostMapping("/me/consumptions/seen")
    public ResponseEntity<SeenResponse> markAllSeen(Authentication auth) {
        return ResponseEntity.ok(service.markAllConsumptionsSeen(currentUserId(auth)));
    }
}
