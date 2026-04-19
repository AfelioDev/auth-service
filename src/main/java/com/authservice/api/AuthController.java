package com.authservice.api;

import com.authservice.api.dto.*;
import com.authservice.config.OAuthStateStore;
import com.authservice.domain.User;
import com.authservice.domain.UserRepository;
import com.authservice.exception.AppException;
import com.authservice.service.RefreshTokenService;
import com.authservice.service.RefreshTokenService.TokenPair;
import com.authservice.service.SocialAuthService;
import com.authservice.service.UserService;
import com.authservice.service.WcaOAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Tag(name = "Auth", description = "Authentication and account management")
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserService userService;
    private final WcaOAuthService wcaOAuthService;
    private final OAuthStateStore stateStore;
    private final RefreshTokenService refreshTokenService;
    private final UserRepository userRepository;
    private final SocialAuthService socialAuthService;

    @Value("${frontend.callback-url:}")
    private String frontendCallbackUrl;

    @Value("${frontend.allowed-redirect-uris:}")
    private String allowedRedirectUris;

    public AuthController(UserService userService, WcaOAuthService wcaOAuthService,
                          OAuthStateStore stateStore, RefreshTokenService refreshTokenService,
                          UserRepository userRepository, SocialAuthService socialAuthService) {
        this.userService = userService;
        this.wcaOAuthService = wcaOAuthService;
        this.stateStore = stateStore;
        this.refreshTokenService = refreshTokenService;
        this.userRepository = userRepository;
        this.socialAuthService = socialAuthService;
    }

    // ── Public endpoints ────────────────────────────────────────────────────

    @Operation(
        summary = "Register with email and password",
        description = "Creates a new account and returns a JWT token."
    )
    @ApiResponse(responseCode = "201", description = "Account created",
        content = @Content(schema = @Schema(implementation = AuthResponse.class)))
    @ApiResponse(responseCode = "409", description = "Email already in use",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "400", description = "Validation error",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(
            @Valid @RequestBody RegisterRequest req,
            @RequestHeader(name = "X-Device-Id", required = false) String deviceId,
            @RequestHeader(name = "X-Device-Name", required = false) String deviceName,
            jakarta.servlet.http.HttpServletRequest httpReq) {
        String token = userService.register(req);
        User user = userRepository.findByEmail(req.email()).orElse(null);
        if (user != null) {
            TokenPair pair = refreshTokenService.issueTokenPair(user, deviceId, deviceName, clientIp(httpReq));
            return ResponseEntity.status(HttpStatus.CREATED).body(authResponse(pair));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("token", token));
    }

    @Operation(
        summary = "Login with email and password",
        description = "Returns a JWT token on successful authentication."
    )
    @ApiResponse(responseCode = "200", description = "Authenticated",
        content = @Content(schema = @Schema(implementation = AuthResponse.class)))
    @ApiResponse(responseCode = "401", description = "Invalid credentials",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @Valid @RequestBody LoginRequest req,
            @RequestHeader(name = "X-Device-Id", required = false) String deviceId,
            @RequestHeader(name = "X-Device-Name", required = false) String deviceName,
            jakarta.servlet.http.HttpServletRequest httpReq) {
        String token = userService.login(req);
        User user = userRepository.findByEmail(req.email()).orElse(null);
        if (user != null) {
            TokenPair pair = refreshTokenService.issueTokenPair(user, deviceId, deviceName, clientIp(httpReq));
            return ResponseEntity.ok(authResponse(pair));
        }
        return ResponseEntity.ok(Map.of("token", token));
    }

    @Operation(
        summary = "Start WCA OAuth2 login",
        description = """
            Redirects the browser to the WCA authorization page to begin the OAuth2 flow.
            After the user authorizes, WCA redirects back to `/auth/wca/callback`.

            **Scopes requested:** `public email`
            - `public` — WCA ID, name, country, avatar
            - `email` — email address

            **Note:** WCA's `wca_id` is `null` for members who haven't competed yet. \
            These users are still supported via `wcaAccountId` (WCA's numeric internal ID).

            ### Flutter / Mobile integration
            Open this URL in the device's native browser (not a WebView):

            ```dart
            launchUrl(
              Uri.parse('https://auth-service-production-bb4a.up.railway.app/auth/wca'),
              mode: LaunchMode.externalApplication,
            );
            ```

            After WCA authenticates the user, the service redirects to the deep link \
            configured in `FRONTEND_CALLBACK_URL` with the JWT as a query parameter:

            ```
            onetimer://callback?token=<jwt>
            ```

            Listen for this deep link in your app to retrieve the token:

            ```dart
            AppLinks().uriLinkStream.listen((uri) {
              if (uri.scheme == 'onetimer' && uri.host == 'callback') {
                final token = uri.queryParameters['token'];
                // store token and navigate
              }
            });
            ```

            Register `onetimer://` as a custom URL scheme in `AndroidManifest.xml` \
            (intent-filter) and `Info.plist` (CFBundleURLSchemes).

            If the user has no account, one is created automatically.
            If the user has an account with the same email, WCA is linked to it.
            """
    )
    @ApiResponse(responseCode = "302", description = "Redirect to WCA authorization page")
    @ApiResponse(responseCode = "400", description = "redirect_uri not in the allowed list",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/wca")
    public ResponseEntity<Void> wcaOAuth(
            @Parameter(
                description = """
                    URL where the token will be sent after a successful login. \
                    Must be listed in `ALLOWED_REDIRECT_URIS`. \
                    If omitted, uses the server's default `FRONTEND_CALLBACK_URL`.

                    **Mobile (Flutter):** `onetimer://callback`
                    **Web:** `https://my-app.com/auth/callback`
                    """,
                example = "onetimer://callback"
            )
            @RequestParam(name = "redirect_uri", required = false) String redirectUri,
            @Parameter(description = "Client-generated UUID identifying the device (propagated to refresh_tokens.device_id).")
            @RequestParam(name = "device_id", required = false) String deviceId,
            @Parameter(description = "Human-readable device label (e.g. \"iPhone de Saúl\").")
            @RequestParam(name = "device_name", required = false) String deviceName) {

        String resolvedUri = resolveRedirectUri(redirectUri);
        String state = stateStore.newState("LOGIN", null, resolvedUri, deviceId, deviceName);
        return redirect(wcaOAuthService.buildAuthorizationUrl(state));
    }

    @Operation(
        summary = "WCA OAuth2 callback",
        description = """
            Handled automatically by WCA — do not call this endpoint directly.

            After the user authenticates, WCA redirects here with a `code`.
            The service exchanges it for a token, resolves the user, and redirects to
            the `redirect_uri` stored in the state with `?token={jwt}`.

            **Response behavior:**
            - If a `redirect_uri` was passed to `/auth/wca`: redirects there with `?token=`.
            - If not, uses `FRONTEND_CALLBACK_URL` env var.
            - If neither is set: returns `{"token": "..."}` as JSON.
            """,
        hidden = true
    )
    @GetMapping("/wca/callback")
    public ResponseEntity<?> wcaCallback(
            @Parameter(description = "Authorization code from WCA") @RequestParam(required = false) String code,
            @Parameter(description = "Anti-CSRF state value") @RequestParam(required = false) String state,
            @Parameter(description = "Error code if authorization was denied") @RequestParam(required = false) String error,
            @Parameter(description = "Human-readable error description") @RequestParam(value = "error_description", required = false) String errorDescription,
            jakarta.servlet.http.HttpServletRequest httpReq) {

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

        String wcaAccessToken = wcaOAuthService.exchangeCodeForToken(code);
        WcaOAuthService.WcaUserInfo wcaUser = wcaOAuthService.getWcaUser(wcaAccessToken);

        if (wcaUser.wcaAccountId() == null) {
            throw new AppException(HttpStatus.BAD_GATEWAY, "WCA did not return a user ID");
        }

        Long linkUserId = "LINK".equals(entry.flow()) ? entry.linkUserId() : null;
        User user = userService.handleWcaCallback(
                wcaUser.wcaAccountId(), wcaUser.wcaId(), wcaUser.name(), wcaUser.email(),
                wcaAccessToken, linkUserId);

        TokenPair pair = refreshTokenService.issueTokenPair(
                user, entry.deviceId(), entry.deviceName(), clientIp(httpReq));

        String callbackUrl = entry.redirectUri();
        if (callbackUrl != null && !callbackUrl.isBlank()) {
            String sep = callbackUrl.contains("?") ? "&" : "?";
            String redirect = callbackUrl
                    + sep + "token=" + java.net.URLEncoder.encode(pair.accessToken(), java.nio.charset.StandardCharsets.UTF_8)
                    + "&refreshToken=" + java.net.URLEncoder.encode(pair.refreshToken(), java.nio.charset.StandardCharsets.UTF_8)
                    + "&expiresIn=" + pair.expiresIn();
            return redirect(redirect);
        }
        return ResponseEntity.ok(authResponse(pair));
    }

    // ── Protected endpoints ─────────────────────────────────────────────────

    @Operation(
        summary = "Link WCA account",
        description = """
            Initiates the WCA OAuth2 flow to link a WCA account to the currently authenticated user.
            Redirects to WCA authorization page. After approval, WCA redirects back to `/auth/wca/callback`.
            """,
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponse(responseCode = "302", description = "Redirect to WCA authorization page")
    @ApiResponse(responseCode = "401", description = "Not authenticated",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/wca/link")
    public ResponseEntity<Void> wcaLink(Authentication auth,
            @Parameter(description = "URL to redirect to after linking. Must be in ALLOWED_REDIRECT_URIS.", example = "onetimer://callback")
            @RequestParam(name = "redirect_uri", required = false) String redirectUri,
            @Parameter(description = "Client-generated UUID identifying the device (propagated to refresh_tokens.device_id).")
            @RequestParam(name = "device_id", required = false) String deviceId,
            @Parameter(description = "Human-readable device label (e.g. \"iPhone de Saúl\").")
            @RequestParam(name = "device_name", required = false) String deviceName) {
        Long userId = currentUserId(auth);
        String resolvedUri = resolveRedirectUri(redirectUri);
        String state = stateStore.newState("LINK", userId, resolvedUri, deviceId, deviceName);
        return redirect(wcaOAuthService.buildAuthorizationUrl(state));
    }

    @Operation(
        summary = "Unlink WCA account",
        description = "Removes the WCA association from the authenticated user. Requires a password to be set on the account to avoid losing all access.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponse(responseCode = "204", description = "WCA account unlinked")
    @ApiResponse(responseCode = "400", description = "Cannot unlink: no password set",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Not authenticated",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @DeleteMapping("/wca/unlink")
    public ResponseEntity<Void> wcaUnlink(Authentication auth) {
        userService.unlinkWca(currentUserId(auth));
        return ResponseEntity.noContent().build();
    }

    @Operation(
        summary = "Set or change password",
        description = """
            Sets a password on the account.
            - **WCA-only users** (no password): can set a password without providing `currentPassword`.
            - **Users with a password**: must provide `currentPassword` to confirm identity.

            After setting a password, WCA can be unlinked via `DELETE /auth/wca/unlink`.
            """,
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponse(responseCode = "204", description = "Password set successfully")
    @ApiResponse(responseCode = "401", description = "Current password incorrect or not authenticated",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/set-password")
    public ResponseEntity<Void> setPassword(Authentication auth,
            @Valid @RequestBody SetPasswordRequest req) {
        userService.setPassword(currentUserId(auth), req.currentPassword(), req.newPassword());
        return ResponseEntity.noContent().build();
    }

    @Operation(
        summary = "Get current user profile",
        description = "Returns the authenticated user's profile.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponse(responseCode = "200", description = "User profile",
        content = @Content(schema = @Schema(implementation = UserDto.class)))
    @ApiResponse(responseCode = "401", description = "Not authenticated",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/me")
    public ResponseEntity<UserDto> me(Authentication auth) {
        return ResponseEntity.ok(userService.getUser(currentUserId(auth)));
    }

    // ── Refresh tokens / session management (ONE-28) ──────────────────────

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refresh(
            @RequestBody Map<String, String> body,
            @RequestHeader(name = "X-Device-Id", required = false) String deviceId,
            @RequestHeader(name = "X-Device-Name", required = false) String deviceName,
            jakarta.servlet.http.HttpServletRequest httpReq) {
        String rt = body != null ? body.get("refreshToken") : null;
        if (rt == null || rt.isBlank()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "refreshToken is required");
        }
        TokenPair pair = refreshTokenService.refresh(rt, deviceId, deviceName, clientIp(httpReq));
        return ResponseEntity.ok(authResponse(pair));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody Map<String, String> body) {
        String rt = body != null ? body.get("refreshToken") : null;
        if (rt != null) refreshTokenService.logout(rt);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<Map<String, Object>>> listSessions(
            Authentication auth,
            @RequestHeader(name = "X-Refresh-Token", required = false) String currentRefresh) {
        return ResponseEntity.ok(
                refreshTokenService.listSessions(currentUserId(auth), currentRefresh));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> revokeSession(Authentication auth, @PathVariable String sessionId) {
        refreshTokenService.revokeSession(currentUserId(auth), java.util.UUID.fromString(sessionId));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sessions/revoke-all-others")
    public ResponseEntity<Void> revokeAllOthers(
            Authentication auth,
            @RequestHeader(name = "X-Refresh-Token", required = false) String currentRefresh) {
        refreshTokenService.revokeAllOthers(currentUserId(auth), currentRefresh);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sessions/revoke-all")
    public ResponseEntity<Void> revokeAll(Authentication auth) {
        refreshTokenService.revokeAllAndBumpVersion(currentUserId(auth));
        return ResponseEntity.noContent().build();
    }

    // ── Social login (Google / Apple) ────────────────────────────────────────

    @Operation(
        summary = "Login with Google",
        description = """
            Validates a Google `id_token` (obtained on the client via the google_sign_in
            plugin with the Web Client ID as `serverClientId`) and returns an auth token
            pair. Creates a new user if the Google account is unknown; auto-links to an
            existing email/password user when the verified email matches.
            """
    )
    @PostMapping("/google")
    public ResponseEntity<Map<String, Object>> loginWithGoogle(
            @RequestBody Map<String, String> body,
            @RequestHeader(name = "X-Device-Id", required = false) String deviceId,
            @RequestHeader(name = "X-Device-Name", required = false) String deviceName,
            jakarta.servlet.http.HttpServletRequest httpReq) {
        if (body == null || body.get("idToken") == null || body.get("idToken").isBlank()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "idToken is required");
        }
        TokenPair pair = socialAuthService.loginWithGoogle(
                body.get("idToken"), deviceId, deviceName, clientIp(httpReq));
        return ResponseEntity.ok(authResponse(pair));
    }

    @Operation(
        summary = "Login with Apple",
        description = """
            Validates an Apple `identity_token` from Sign in with Apple and returns an
            auth token pair. Accepts both native iOS tokens (aud = bundle id) and
            Android/web tokens (aud = Services ID). On first sign-in the client can pass
            `fullName` which is used as the display name seed (Apple only returns the
            name in the first response).
            """
    )
    @PostMapping("/apple")
    public ResponseEntity<Map<String, Object>> loginWithApple(
            @RequestBody Map<String, String> body,
            @RequestHeader(name = "X-Device-Id", required = false) String deviceId,
            @RequestHeader(name = "X-Device-Name", required = false) String deviceName,
            jakarta.servlet.http.HttpServletRequest httpReq) {
        if (body == null || body.get("identityToken") == null || body.get("identityToken").isBlank()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "identityToken is required");
        }
        TokenPair pair = socialAuthService.loginWithApple(
                body.get("identityToken"), body.get("fullName"),
                deviceId, deviceName, clientIp(httpReq));
        return ResponseEntity.ok(authResponse(pair));
    }

    // ── Health ───────────────────────────────────────────────────────────────

    @Operation(summary = "Health check", description = "Returns `{\"status\": \"UP\"}` when the service is running.")
    @ApiResponse(responseCode = "200", description = "Service is healthy")
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Long currentUserId(Authentication auth) {
        return Long.parseLong((String) auth.getPrincipal());
    }

    private Map<String, Object> authResponse(TokenPair pair) {
        Map<String, Object> r = new java.util.LinkedHashMap<>();
        r.put("token", pair.accessToken());            // backwards compat
        r.put("accessToken", pair.accessToken());
        r.put("refreshToken", pair.refreshToken());
        r.put("expiresIn", pair.expiresIn());
        return r;
    }

    private String clientIp(jakarta.servlet.http.HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        String cfIp = req.getHeader("CF-Connecting-IP");
        if (cfIp != null) return cfIp;
        return req.getRemoteAddr();
    }

    private ResponseEntity<Void> redirect(String url) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .header("Location", url)
                .build();
    }

    /**
     * Resolves and validates the client-supplied redirect_uri against the whitelist.
     * Falls back to FRONTEND_CALLBACK_URL if no uri is provided.
     * Throws 400 if a uri is provided but is not in the allowed list.
     */
    private String resolveRedirectUri(String requested) {
        if (requested == null || requested.isBlank()) {
            return (frontendCallbackUrl != null && !frontendCallbackUrl.isBlank())
                    ? frontendCallbackUrl : null;
        }
        List<String> allowed = (allowedRedirectUris != null && !allowedRedirectUris.isBlank())
                ? Arrays.asList(allowedRedirectUris.split(","))
                : List.of();

        boolean isAllowed = allowed.stream()
                .map(String::trim)
                .anyMatch(requested::startsWith);

        if (!isAllowed) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "redirect_uri not allowed: " + requested);
        }
        return requested;
    }
}
