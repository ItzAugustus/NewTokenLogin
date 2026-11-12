package com.kcl.tokenlogin.client.auth;

import java.util.Locale;
import java.util.UUID;

/**
 * One stored Minecraft account. The JSON shape of this class is the on-disk
 * format of {@code config/tokenlogin-accounts.json}.
 */
public final class Account {
    private String profileId;
    private String name;
    private String refreshToken;
    private String accessToken;
    private long accessExpiresAt;
    private long importedAt;
    private long lastRefreshAttempt;
    private int failedRefreshes;
    private boolean refreshInvalid;

    /** Runtime only: a refresh request is currently in flight. */
    private transient boolean refreshing;

    public Account() {
    }

    public String getProfileId() {
        return profileId;
    }

    public void setProfileId(String profileId) {
        this.profileId = profileId;
    }

    /** Profile id without dashes, lower case, or {@code null} when malformed. */
    public String normalizedId() {
        if (profileId == null) {
            return null;
        }
        String id = profileId.replace("-", "").trim().toLowerCase(Locale.ROOT);
        return id.isEmpty() ? null : id;
    }

    public UUID uuid() {
        String hex = normalizedId();
        if (hex == null || hex.length() != 32) {
            return null;
        }
        try {
            long most = Long.parseUnsignedLong(hex.substring(0, 16), 16);
            long least = Long.parseUnsignedLong(hex.substring(16, 32), 16);
            return new UUID(most, least);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public String getName() {
        return name == null || name.isBlank() ? "?" : name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public long getAccessExpiresAt() {
        return accessExpiresAt;
    }

    public void setAccessExpiresAt(long accessExpiresAt) {
        this.accessExpiresAt = accessExpiresAt;
    }

    public long getImportedAt() {
        return importedAt;
    }

    public void setImportedAt(long importedAt) {
        this.importedAt = importedAt;
    }

    public long getLastRefreshAttempt() {
        return lastRefreshAttempt;
    }

    public void setLastRefreshAttempt(long lastRefreshAttempt) {
        this.lastRefreshAttempt = lastRefreshAttempt;
    }

    public int getFailedRefreshes() {
        return failedRefreshes;
    }

    public void setFailedRefreshes(int failedRefreshes) {
        this.failedRefreshes = failedRefreshes;
    }

    public boolean isRefreshInvalid() {
        return refreshInvalid;
    }

    public void setRefreshInvalid(boolean refreshInvalid) {
        this.refreshInvalid = refreshInvalid;
    }

    public boolean isRefreshing() {
        return refreshing;
    }

    public void setRefreshing(boolean refreshing) {
        this.refreshing = refreshing;
    }

    public long remainingAccessMillis() {
        return accessExpiresAt - System.currentTimeMillis();
    }

    /** An access token that is still valid long enough to be used right now. */
    public boolean hasUsableAccessToken() {
        return !refreshInvalid
            && accessToken != null
            && !accessToken.isBlank()
            && remainingAccessMillis() > 60_000L;
    }
}
