package com.authservice.api;

import com.authservice.api.dto.*;
import com.authservice.config.OAuthStateStore;
import com.authservice.exception.AppException;
import com.authservice.service.UserService;
import com.authservice.service.WcaOAuthService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserService userService;
    private final WcaOAuthService wcaOAuthService;
    private final OAuthStateStore stateStore;

    @Value("${frontend.callback-url:}")
    private String frontendCallbackUrl;

    public AuthController(UserService userService, WcaOAuthService wcaOAuthService,
                          OAuthStateStore stateStore) {
        this.userService = userService;
        this.wcaOAuthService = wcaOAuthService;
        this.stateStore = stateStore;
    }

    // ── Public endpoints ────────────────────────────────────────────────────

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        String token = userService.register(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(new AuthResponse(token));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        String token = userService.login(req);
        return ResponseEntity.ok(new AuthResponse(token));
    }

    /**
     * Initiates the WCA OAuth2 login/register flow.
     * Redirects the browser to WCA's authorization page.
     */
    @GetMapping("/wca")
    public ResponseEntity<Void> wcaOAuth() {
        String state = stateStore.newState("LOGIN", null);
        return redirect(wcaOAuthService.buildAuthorizationUrl(state));
    }

    /**
     * WCA OAuth2 callback. Handles both the login/register and link flows,
     * distinguished by the state entry stored before the redirect.
     */
    @GetMapping("/wca/callback")
    public ResponseEntity<?> wcaCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            @RequestParam(value = "error_description", required = false) String errorDescription) {

        if (error != null) {
            throw new AppException(HttpStatus.UNAUTHORIZED,
                    "WCA OAuth denied: " + (errorDescription != null ? errorDescription : error));
        }
        if (code == null || state == null) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Missing code or state parameter");
        }

        OAuthStateStore.StateEntry entry = stateStore.consume(state)
                .orElseThrow(() -> new AppException(HttpStatus.BAD_REQUEST,
                        "Invalid or expired OAuth state. Please start the flow again."));

        String accessToken = wcaOAuthService.exchangeCodeForToken(code);
        WcaOAuthService.WcaUserInfo wcaUser = wcaOAuthService.getWcaUser(accessToken);

        if (wcaUser.wcaId() == null) {
            throw new AppException(HttpStatus.BAD_GATEWAY, "WCA did not return a WCA ID");
        }

        Long linkUserId = "LINK".equals(entry.flow()) ? entry.linkUserId() : null;
        String jwt = userService.handleWcaCallback(
                wcaUser.wcaId(), wcaUser.name(), wcaUser.email(), accessToken, linkUserId);

        // If a frontend URL is configured, redirect with token in query param
        if (frontendCallbackUrl != null && !frontendCallbackUrl.isBlank()) {
            return redirect(frontendCallbackUrl + "?token=" + jwt);
        }
        return ResponseEntity.ok(new AuthResponse(jwt));
    }

    // ── Protected endpoints ─────────────────────────────────────────────────

    /**
     * Initiates the WCA link flow for an already authenticated user.
     * The current user's ID is embedded in the state so the callback
     * can associate the WCA ID to this account.
     */
    @GetMapping("/wca/link")
    public ResponseEntity<Void> wcaLink(Authentication auth) {
        Long userId = currentUserId(auth);
        String state = stateStore.newState("LINK", userId);
        return redirect(wcaOAuthService.buildAuthorizationUrl(state));
    }

    /**
     * Removes the WCA association from the authenticated user.
     * Requires the user to have a password set (otherwise they'd lose all access).
     */
    @DeleteMapping("/wca/unlink")
    public ResponseEntity<Void> wcaUnlink(Authentication auth) {
        userService.unlinkWca(currentUserId(auth));
        return ResponseEntity.noContent().build();
    }

    /**
     * Returns the authenticated user's profile.
     */
    @GetMapping("/me")
    public ResponseEntity<UserDto> me(Authentication auth) {
        return ResponseEntity.ok(userService.getUser(currentUserId(auth)));
    }

    // ── Health ───────────────────────────────────────────────────────────────

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Long currentUserId(Authentication auth) {
        return Long.parseLong((String) auth.getPrincipal());
    }

    private ResponseEntity<Void> redirect(String url) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .header("Location", url)
                .build();
    }
}
