package com.authservice.api;

import com.authservice.api.dto.InappRecordsDto;
import com.authservice.service.InappRecordsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    public UsersController(InappRecordsService inappRecordsService) {
        this.inappRecordsService = inappRecordsService;
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
}
