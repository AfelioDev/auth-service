package com.authservice.api;

import com.authservice.api.dto.AvatarsDto.*;
import com.authservice.service.AvatarService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Avatar system HTTP API (Tarea 14 / ONE-14).
 *
 * Mirror of the spec endpoints: catalog discovery, the per-user snapshot,
 * onboarding pick, free equip, and the gated initial-free change with
 * options preview. JWT scopes every {@code /me/*} call to the principal so
 * users can never read or write someone else's inventory.
 */
@RestController
@RequestMapping("/avatars")
public class AvatarsController {

    private final AvatarService service;

    public AvatarsController(AvatarService service) {
        this.service = service;
    }

    private Long currentUserId(Authentication auth) {
        return Long.parseLong((String) auth.getPrincipal());
    }

    // ── Catalog ──────────────────────────────────────────────────────────

    @GetMapping("/catalog")
    public ResponseEntity<CatalogResponse> getCatalog() {
        return ResponseEntity.ok(service.getCatalog());
    }

    @GetMapping("/onboarding-options")
    public ResponseEntity<CatalogResponse> getOnboardingOptions() {
        return ResponseEntity.ok(service.getOnboardingOptions());
    }

    // ── Per-user ─────────────────────────────────────────────────────────

    @GetMapping("/me")
    public ResponseEntity<MeResponse> getMe(Authentication auth) {
        return ResponseEntity.ok(service.getMe(currentUserId(auth)));
    }

    @PostMapping("/me/initial")
    public ResponseEntity<MeResponse> pickInitial(
            Authentication auth, @RequestBody InitialPickRequest body) {
        return ResponseEntity.ok(service.pickInitial(
                currentUserId(auth), body == null ? null : body.avatarId()));
    }

    @PutMapping("/me/equipped")
    public ResponseEntity<MeResponse> equip(
            Authentication auth, @RequestBody EquipRequest body) {
        return ResponseEntity.ok(service.equip(
                currentUserId(auth), body == null ? null : body.avatarId()));
    }

    @GetMapping("/me/initial-change-options")
    public ResponseEntity<InitialChangeOptionsResponse> getInitialChangeOptions(
            Authentication auth) {
        return ResponseEntity.ok(service.getInitialChangeOptions(currentUserId(auth)));
    }

    @PostMapping("/me/initial-change")
    public ResponseEntity<MeResponse> initialChange(
            Authentication auth, @RequestBody InitialChangeRequest body) {
        return ResponseEntity.ok(service.initialChange(
                currentUserId(auth), body == null ? null : body.newAvatarId()));
    }
}
