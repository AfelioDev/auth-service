package com.authservice.domain;

import java.time.LocalDateTime;

public class VersionRequirement {

    private String platform;      // ios | android
    private String minVersion;
    private String latestVersion;
    private String storeUrl;
    private String messageEs;
    private String messageEn;
    private LocalDateTime updatedAt;

    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }

    public String getMinVersion() { return minVersion; }
    public void setMinVersion(String minVersion) { this.minVersion = minVersion; }

    public String getLatestVersion() { return latestVersion; }
    public void setLatestVersion(String latestVersion) { this.latestVersion = latestVersion; }

    public String getStoreUrl() { return storeUrl; }
    public void setStoreUrl(String storeUrl) { this.storeUrl = storeUrl; }

    public String getMessageEs() { return messageEs; }
    public void setMessageEs(String messageEs) { this.messageEs = messageEs; }

    public String getMessageEn() { return messageEn; }
    public void setMessageEn(String messageEn) { this.messageEn = messageEn; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
