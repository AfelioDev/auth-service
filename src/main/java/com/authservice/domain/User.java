package com.authservice.domain;

import java.time.LocalDateTime;

public class User {

    private Long id;
    private String name;
    private String displayName;  // user_profile.display_name override (nullable)
    private String email;
    private String passwordHash;
    private Long wcaAccountId;   // WCA numeric id — always present for OAuth users
    private String wcaId;        // WCA competitor id (e.g. "2009ZEMD01") — null if not competed
    private String wcaAccessToken;
    private int tokenVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    /**
     * Returns the user-chosen displayName when set, otherwise the WCA/registration name.
     * Single source of truth for "the name to render across the app".
     */
    public String getResolvedName() {
        return (displayName != null && !displayName.isBlank()) ? displayName : name;
    }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public Long getWcaAccountId() { return wcaAccountId; }
    public void setWcaAccountId(Long wcaAccountId) { this.wcaAccountId = wcaAccountId; }

    public String getWcaId() { return wcaId; }
    public void setWcaId(String wcaId) { this.wcaId = wcaId; }

    public String getWcaAccessToken() { return wcaAccessToken; }
    public void setWcaAccessToken(String wcaAccessToken) { this.wcaAccessToken = wcaAccessToken; }

    public int getTokenVersion() { return tokenVersion; }
    public void setTokenVersion(int tokenVersion) { this.tokenVersion = tokenVersion; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
