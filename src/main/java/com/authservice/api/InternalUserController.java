package com.authservice.api;

import com.authservice.api.dto.InternalUserDto;
import com.authservice.domain.User;
import com.authservice.domain.UserRepository;
import com.authservice.exception.AppException;
import com.authservice.service.AvatarService;
import com.authservice.service.StreakService;
import com.authservice.service.VersionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Internal service-to-service endpoint.
 * Accessible only within the one-timer-internal Docker network.
 * Traefik does NOT expose /internal/** — it is only reachable by other containers.
 */
@RestController
@RequestMapping("/internal")
public class InternalUserController {

    private final UserRepository userRepository;
    private final VersionService versionService;
    private final StreakService streakService;
    private final AvatarService avatarService;

    public InternalUserController(UserRepository userRepository,
                                   VersionService versionService,
                                   StreakService streakService,
                                   AvatarService avatarService) {
        this.userRepository = userRepository;
        this.versionService = versionService;
        this.streakService = streakService;
        this.avatarService = avatarService;
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<InternalUserDto> getUser(@PathVariable Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User not found"));
        return ResponseEntity.ok(new InternalUserDto(
                user.getId(),
                user.getResolvedName(),
                user.getWcaId(),
                avatarService.getEquippedImageUrl(user.getId())  // ONE-14: equipped avatar
        ));
    }

    /**
     * Batch lookup of resolved display names by WCA competitor IDs.
     * Used by wca-rest-api to enrich rankings and person profiles with One Timer
     * display name overrides — when a wcaId belongs to a registered user with a
     * custom display_name, the WCA proxy must return that value instead of the
     * official WCA name.
     *
     * Body: {wcaIds: ["2014ORTS01", ...]}
     * Returns: {wcaIds: {wcaId → resolvedName}}  (only includes entries that mapped)
     */
    @PostMapping("/users/displaynames-by-wca-ids")
    public ResponseEntity<Map<String, Object>> getDisplayNamesByWcaIds(
            @RequestBody Map<String, java.util.List<String>> body) {
        java.util.List<String> wcaIds = body != null ? body.get("wcaIds") : null;
        Map<String, String> resolved = userRepository.findResolvedNamesByWcaIds(
                wcaIds != null ? wcaIds : java.util.List.of());
        return ResponseEntity.ok(Map.of("displayNames", resolved));
    }

    /**
     * Returns streaks for a list of user IDs in a single call (no N+1).
     * Used by social-service to enrich the friends list with streak badges.
     * Staleness-corrected: if a user hasn't solved in >1 day, currentStreak=0.
     */
    @PostMapping("/streaks/batch")
    public ResponseEntity<Map<String, Object>> getStreaksBatch(
            @RequestBody Map<String, java.util.List<Long>> body) {
        java.util.List<Long> userIds = body != null ? body.get("userIds") : null;
        return ResponseEntity.ok(Map.of("streaks",
                streakService.getStreaksBatch(userIds != null ? userIds : java.util.List.of())));
    }

    /**
     * Admin endpoint to bump the version requirements for a platform.
     * Called manually (kubectl exec + curl, or a future admin tool) when
     * publishing a release that needs to force old clients to update.
     *
     * Lives under /internal/** so it is not exposed via Traefik — only
     * callable from inside the one-timer-internal Docker network.
     */
    @PutMapping("/version/requirements/{platform}")
    public ResponseEntity<Void> updateVersionRequirements(
            @PathVariable String platform,
            @RequestBody Map<String, String> body) {
        versionService.update(
                platform,
                body.get("minVersion"),
                body.get("latestVersion"),
                body.get("storeUrl"),
                body.get("messageEs"),
                body.get("messageEn")
        );
        return ResponseEntity.noContent().build();
    }
}
