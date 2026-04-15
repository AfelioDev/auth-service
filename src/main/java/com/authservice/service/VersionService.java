package com.authservice.service;

import com.authservice.domain.VersionRequirement;
import com.authservice.domain.VersionRequirementRepository;
import com.authservice.exception.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class VersionService {

    private final VersionRequirementRepository repo;

    public VersionService(VersionRequirementRepository repo) {
        this.repo = repo;
    }

    /**
     * Resolves the version requirements for the given platform and decides
     * whether the caller must be forced to update.
     *
     * Rule: `forceUpdate = true` iff the current version's major or minor
     * component is BEHIND the minimum required version. Patch differences
     * NEVER force an update (1.2.6 vs 1.2.7 -> false even if min=1.2.7).
     *
     * Examples:
     *   current=1.2.6, min=1.3.0 -> true   (minor behind)
     *   current=1.2.6, min=2.0.0 -> true   (major behind)
     *   current=1.3.1, min=1.3.0 -> false  (ahead)
     *   current=1.3.1, min=1.3.5 -> false  (patch behind only)
     *   current=1.2.6, min=1.2.9 -> false  (patch behind only)
     */
    public VersionRequirementsResult resolve(String platform, String currentRaw) {
        String normalizedPlatform = platform == null ? "" : platform.toLowerCase();
        if (!"ios".equals(normalizedPlatform) && !"android".equals(normalizedPlatform)) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Unknown platform '" + platform + "' (expected 'ios' or 'android')");
        }

        VersionRequirement row = repo.findByPlatform(normalizedPlatform)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "No version requirements configured for " + normalizedPlatform));

        boolean forceUpdate = false;
        if (currentRaw != null && !currentRaw.isBlank()) {
            SemVer current = SemVer.parse(currentRaw);
            SemVer min = SemVer.parse(row.getMinVersion());
            forceUpdate = isBehindMajorOrMinor(current, min);
        }

        return new VersionRequirementsResult(
                row.getPlatform(),
                row.getMinVersion(),
                row.getLatestVersion(),
                row.getStoreUrl(),
                row.getMessageEs(),
                row.getMessageEn(),
                forceUpdate
        );
    }

    /**
     * Returns true iff `current` is behind `min` in its major or minor
     * component. Patch-level differences are ignored.
     */
    static boolean isBehindMajorOrMinor(SemVer current, SemVer min) {
        if (current.major < min.major) return true;
        if (current.major > min.major) return false;
        return current.minor < min.minor;
    }

    /** Updates the requirements row for the given platform (admin use). */
    public void update(String platform, String minVersion, String latestVersion,
                       String storeUrl, String messageEs, String messageEn) {
        String normalized = platform == null ? "" : platform.toLowerCase();
        if (!"ios".equals(normalized) && !"android".equals(normalized)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Unknown platform '" + platform + "'");
        }
        // Validate both versions parse before writing anything.
        SemVer.parse(minVersion);
        SemVer.parse(latestVersion);
        repo.update(normalized, minVersion, latestVersion, storeUrl, messageEs, messageEn);
    }

    // ── Result DTO ────────────────────────────────────────────────────────────

    public record VersionRequirementsResult(
            String platform,
            String minVersion,
            String latestVersion,
            String storeUrl,
            String messageEs,
            String messageEn,
            boolean forceUpdate
    ) {}

    // ── Minimal semver parser ────────────────────────────────────────────────
    //
    // The Flutter app uses simple `major.minor.patch` (+ optional build number
    // after a `+`, like `1.2.6+16`). We strip the build metadata and parse the
    // three numeric components. Non-numeric pre-release tags (`-beta`, etc.)
    // are also stripped. Full semver 2.0 comparison is overkill here.

    static final class SemVer {
        final int major;
        final int minor;
        final int patch;

        SemVer(int major, int minor, int patch) {
            this.major = major;
            this.minor = minor;
            this.patch = patch;
        }

        static SemVer parse(String raw) {
            if (raw == null) throw new AppException(HttpStatus.BAD_REQUEST, "version is null");
            String s = raw.trim();
            // Strip build metadata (+16) and pre-release (-beta.1) suffixes.
            int plus = s.indexOf('+');
            if (plus >= 0) s = s.substring(0, plus);
            int dash = s.indexOf('-');
            if (dash >= 0) s = s.substring(0, dash);
            String[] parts = s.split("\\.");
            if (parts.length == 0 || parts.length > 3) {
                throw new AppException(HttpStatus.BAD_REQUEST, "invalid version: " + raw);
            }
            try {
                int major = Integer.parseInt(parts[0]);
                int minor = parts.length >= 2 ? Integer.parseInt(parts[1]) : 0;
                int patch = parts.length >= 3 ? Integer.parseInt(parts[2]) : 0;
                if (major < 0 || minor < 0 || patch < 0) {
                    throw new AppException(HttpStatus.BAD_REQUEST, "negative version component: " + raw);
                }
                return new SemVer(major, minor, patch);
            } catch (NumberFormatException e) {
                throw new AppException(HttpStatus.BAD_REQUEST, "invalid version: " + raw);
            }
        }
    }
}
