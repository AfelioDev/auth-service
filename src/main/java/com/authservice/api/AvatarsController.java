package com.authservice.api;

import com.authservice.api.dto.AvatarsDto.*;
import com.authservice.service.AvatarService;
import org.springframework.http.HttpHeaders;
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
 *
 * Every endpoint that returns catalog data accepts the {@code Accept-Language}
 * header (ONE-14 i18n follow-up). The backend resolves the locale and
 * returns {@code description} in that language, falling back to Spanish (the
 * base copy) when no translation exists. {@code name} is identity-of-marca
 * and never translated.
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
    public ResponseEntity<CatalogResponse> getCatalog(
            @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return ResponseEntity.ok(service.getCatalog(acceptLanguage));
    }

    @GetMapping("/onboarding-options")
    public ResponseEntity<CatalogResponse> getOnboardingOptions(
            @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return ResponseEntity.ok(service.getOnboardingOptions(acceptLanguage));
    }

    // ── Per-user ─────────────────────────────────────────────────────────

    @GetMapping("/me")
    public ResponseEntity<MeResponse> getMe(
            Authentication auth,
            @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return ResponseEntity.ok(service.getMe(currentUserId(auth), acceptLanguage));
    }

    @PostMapping("/me/initial")
    public ResponseEntity<MeResponse> pickInitial(
            Authentication auth, @RequestBody InitialPickRequest body,
            @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return ResponseEntity.ok(service.pickInitial(
                currentUserId(auth), body == null ? null : body.avatarId(), acceptLanguage));
    }

    @PutMapping("/me/equipped")
    public ResponseEntity<MeResponse> equip(
            Authentication auth, @RequestBody EquipRequest body,
            @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return ResponseEntity.ok(service.equip(
                currentUserId(auth), body == null ? null : body.avatarId(), acceptLanguage));
    }

    @GetMapping("/me/initial-change-options")
    public ResponseEntity<InitialChangeOptionsResponse> getInitialChangeOptions(
            Authentication auth,
            @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return ResponseEntity.ok(service.getInitialChangeOptions(currentUserId(auth), acceptLanguage));
    }

    @PostMapping("/me/initial-change")
    public ResponseEntity<MeResponse> initialChange(
            Authentication auth, @RequestBody InitialChangeRequest body,
            @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return ResponseEntity.ok(service.initialChange(
                currentUserId(auth), body == null ? null : body.newAvatarId(), acceptLanguage));
    }
}
