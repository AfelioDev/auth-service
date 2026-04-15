package com.authservice.api;

import com.authservice.api.dto.InternalUserDto;
import com.authservice.domain.User;
import com.authservice.domain.UserRepository;
import com.authservice.exception.AppException;
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

    public InternalUserController(UserRepository userRepository, VersionService versionService) {
        this.userRepository = userRepository;
        this.versionService = versionService;
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<InternalUserDto> getUser(@PathVariable Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User not found"));
        return ResponseEntity.ok(new InternalUserDto(
                user.getId(),
                user.getName(),
                user.getWcaId(),
                null  // avatarUrl — not implemented yet
        ));
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
