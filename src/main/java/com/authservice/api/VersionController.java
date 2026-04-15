package com.authservice.api;

import com.authservice.service.VersionService;
import com.authservice.service.VersionService.VersionRequirementsResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Public (unauthenticated) endpoint used by the mobile client on every cold
 * start to decide whether the user must be force-updated. Cached on the
 * client for a few minutes to avoid hammering this endpoint.
 *
 * Whitelisted in SecurityConfig under `/version/**`.
 */
@RestController
public class VersionController {

    private final VersionService versionService;

    public VersionController(VersionService versionService) {
        this.versionService = versionService;
    }

    @GetMapping("/version/requirements")
    public ResponseEntity<Map<String, Object>> requirements(
            @RequestParam(name = "platform") String platform,
            @RequestParam(name = "current", required = false) String current) {

        VersionRequirementsResult r = versionService.resolve(platform, current);

        // Stable, explicit field order for the JSON contract.
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("platform", r.platform());
        body.put("minVersion", r.minVersion());
        body.put("latestVersion", r.latestVersion());
        body.put("forceUpdate", r.forceUpdate());
        body.put("storeUrl", r.storeUrl());
        body.put("messageEs", r.messageEs());
        body.put("messageEn", r.messageEn());
        return ResponseEntity.ok(body);
    }
}
