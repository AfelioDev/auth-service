package com.authservice.api;

import com.authservice.api.dto.*;
import com.authservice.config.OAuthStateStore;
import com.authservice.exception.AppException;
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

    @Value("${frontend.callback-url:}")
    private String frontendCallbackUrl;

    @Value("${frontend.allowed-redirect-uris:}")
    private String allowedRedirectUris;

    public AuthController(UserService userService, WcaOAuthService wcaOAuthService,
                          OAuthStateStore stateStore) {
        this.userService = userService;
        this.wcaOAuthService = wcaOAuthService;
        this.stateStore = stateStore;
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
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        String token = userService.register(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(new AuthResponse(token));
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
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        String token = userService.login(req);
        return ResponseEntity.ok(new AuthResponse(token));
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
            @RequestParam(required = false) String redirectUri) {

        String resolvedUri = resolveRedirectUri(redirectUri);
        String state = stateStore.newState("LOGIN", null, resolvedUri);
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
            @Parameter(description = "Human-readable error description") @RequestParam(value = "error_description", required = false) String errorDescription) {

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

        if (wcaUser.wcaAccountId() == null) {
            throw new AppException(HttpStatus.BAD_GATEWAY, "WCA did not return a user ID");
        }

        Long linkUserId = "LINK".equals(entry.flow()) ? entry.linkUserId() : null;
        String jwt = userService.handleWcaCallback(
                wcaUser.wcaAccountId(), wcaUser.wcaId(), wcaUser.name(), wcaUser.email(),
                accessToken, linkUserId);

        String callbackUrl = entry.redirectUri();
        if (callbackUrl != null && !callbackUrl.isBlank()) {
            return redirect(callbackUrl + "?token=" + jwt);
        }
        return ResponseEntity.ok(new AuthResponse(jwt));
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
            @RequestParam(required = false) String redirectUri) {
        Long userId = currentUserId(auth);
        String resolvedUri = resolveRedirectUri(redirectUri);
        String state = stateStore.newState("LINK", userId, resolvedUri);
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
