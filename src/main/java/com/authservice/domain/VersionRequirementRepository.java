package com.authservice.domain;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class VersionRequirementRepository {

    private final JdbcTemplate jdbc;

    public VersionRequirementRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<VersionRequirement> findByPlatform(String platform) {
        List<VersionRequirement> rows = jdbc.query(
                "SELECT * FROM version_requirements WHERE platform = ?", mapper(), platform);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public void update(String platform, String minVersion, String latestVersion,
                       String storeUrl, String messageEs, String messageEn) {
        jdbc.update(
                "UPDATE version_requirements " +
                "   SET min_version = ?, latest_version = ?, store_url = ?, " +
                "       message_es = ?, message_en = ?, updated_at = NOW() " +
                " WHERE platform = ?",
                minVersion, latestVersion, storeUrl, messageEs, messageEn, platform);
    }

    private RowMapper<VersionRequirement> mapper() {
        return (rs, rowNum) -> {
            VersionRequirement v = new VersionRequirement();
            v.setPlatform(rs.getString("platform"));
            v.setMinVersion(rs.getString("min_version"));
            v.setLatestVersion(rs.getString("latest_version"));
            v.setStoreUrl(rs.getString("store_url"));
            v.setMessageEs(rs.getString("message_es"));
            v.setMessageEn(rs.getString("message_en"));
            v.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
            return v;
        };
    }
}
