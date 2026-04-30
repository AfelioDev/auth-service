package com.authservice.api;

import com.authservice.api.dto.InappRecordsDto;
import com.authservice.domain.User;
import com.authservice.domain.UserRepository;
import com.authservice.exception.AppException;
import com.authservice.service.AvatarService;
import com.authservice.service.InappRecordsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Public read endpoints scoped to a user other than the caller — what a
 * profile visitor can see. The caller's identity is the JWT principal; the
 * `userId` path variable is the profile being viewed.
 */
@Tag(name = "Users", description = "Public read endpoints scoped to a user (profile visitor)")
@RestController
@RequestMapping("/users")
public class UsersController {

    private final InappRecordsService inappRecordsService;
    private final UserRepository userRepository;
    private final AvatarService avatarService;

    public UsersController(InappRecordsService inappRecordsService,
                            UserRepository userRepository,
                            AvatarService avatarService) {
        this.inappRecordsService = inappRecordsService;
        this.userRepository = userRepository;
        this.avatarService = avatarService;
    }

    @Operation(
        summary = "Resolve a wcaId to a One Timer user",
        description = """
            Maps a WCA competitor ID (e.g. `2016CORT01`) to the internal user
            of the One Timer account that has it linked, when one exists.
            Used by the client to bridge surfaces that only carry the wcaId
            (rankings, competition results) into surfaces scoped by internal
            user id (in-app records, friend streaks).

            Returns 404 when the wcaId is unknown to One Timer (the person
            exists in WCA but has not registered or has not linked their
            account yet). JWT required; no friendship check — the same
            policy as `/users/{userId}/inapp-records`.
            """
    )
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/by-wca/{wcaId}")
    public ResponseEntity<Map<String, Object>> getUserByWcaId(
            @Parameter(description = "WCA competitor id", example = "2016CORT01")
            @PathVariable String wcaId) {
        User user = userRepository.findByWcaId(wcaId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "wcaId not linked to a One Timer user"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", user.getId());
        body.put("displayName", user.getResolvedName());
        body.put("wcaId", user.getWcaId());
        body.put("avatarUrl", avatarService.getEquippedImageUrl(user.getId()));
        return ResponseEntity.ok(body);
    }

    @Operation(
        summary = "Resolve a public friend code to a One Timer user (ONE-40)",
        description = """
            Maps a public 8-digit friend code (e.g. `47281926`) to the
            user that owns it. Used by the client when the user types or
            pastes a code into the "Add friend" sheet — the response
            carries the minimum we want surfaced before sending a request:
            display name and equipped avatar so the requester can confirm
            who they are about to friend.

            Returns 404 when the code does not exist. Accepts the code
            with optional separators (`4728-1926`, `4728 1926`); only
            digits are matched against the `friend_code` column.
            """
    )
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/by-code/{friendCode}")
    public ResponseEntity<Map<String, Object>> getUserByFriendCode(
            @Parameter(description = "Public 8-digit friend code", example = "47281926")
            @PathVariable String friendCode) {
        String normalized = friendCode == null ? "" : friendCode.replaceAll("\\D", "");
        if (normalized.length() != 8) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "friendCode must be exactly 8 digits");
        }
        User user = userRepository.findByFriendCode(normalized)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "friend_code_not_found"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", user.getId());
        body.put("displayName", user.getResolvedName());
        body.put("wcaId", user.getWcaId());
        body.put("avatarUrl", avatarService.getEquippedImageUrl(user.getId()));
        body.put("friendCode", user.getFriendCode());
        return ResponseEntity.ok(body);
    }

    @Operation(
        summary = "In-app records for a user",
        description = """
            Returns best single and best Ao5 per category for the requested
            user, computed from their synced solves (ONE-27). Sessions that
            the owner excluded via the in-app records configuration (ONE-17)
            are filtered out before computation, so a visitor sees the same
            numbers the owner sees.

            Response is intentionally minimal: no session list, no exclusion
            flags, no raw solves — just the aggregated records. If the user
            has no synced solves, `records` is an empty array.
            """
    )
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/{userId}/inapp-records")
    public ResponseEntity<InappRecordsDto.Response> getInappRecords(
            @Parameter(description = "Internal user ID of the profile being viewed", example = "36")
            @PathVariable Long userId) {
        return ResponseEntity.ok(inappRecordsService.compute(userId));
    }

    @Operation(
        summary = "Bulk-resolve wcaIds → userId/avatar (ONE-39)",
        description = """
            Batch counterpart of `GET /users/by-wca/{wcaId}`. Lists like
            ranking, competition results and search render dozens to
            hundreds of WCA people at once; resolving each one with the
            singular endpoint fans out N requests to auth-service per
            page. This endpoint takes up to 200 wcaIds in a single call
            and returns the One-Timer profile for each id that maps to a
            registered user.

            Only registered wcaIds appear in the `resolutions` map —
            unmapped ids are simply omitted (equivalent to the singular
            endpoint's 404). Cap is enforced server-side; clients with
            larger lists must batch from their side.
            """
    )
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/by-wca/batch")
    public ResponseEntity<Map<String, Object>> resolveWcaIdsBatch(
            @RequestBody Map<String, List<String>> body) {
        List<String> wcaIds = body == null ? null : body.get("wcaIds");
        if (wcaIds == null) wcaIds = List.of();
        if (wcaIds.size() > 200) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "wcaIds may contain at most 200 entries per batch");
        }
        Map<String, Object> resolutions = new LinkedHashMap<>();
        for (String wcaId : wcaIds) {
            if (wcaId == null || wcaId.isBlank()) continue;
            userRepository.findByWcaId(wcaId).ifPresent(user -> {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("userId", user.getId());
                entry.put("displayName", user.getResolvedName());
                entry.put("avatarUrl", avatarService.getEquippedImageUrl(user.getId()));
                resolutions.put(wcaId, entry);
            });
        }
        return ResponseEntity.ok(Map.of("resolutions", resolutions));
    }
}
