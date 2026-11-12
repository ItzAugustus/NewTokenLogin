package com.kcl.tokenlogin.client.auth;

import com.kcl.tokenlogin.client.mixin.MinecraftClientSessionAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;

import java.util.Optional;
import java.util.UUID;

/**
 * Swaps the running client's {@link Session} so the game authenticates with the
 * selected account.
 */
public final class UserManager {
    private UserManager() {
    }

    public static Session getCurrentSession() {
        return ((MinecraftClientSessionAccessor) (Object) MinecraftClient.getInstance()).tokenlogin$getSession();
    }

    public static boolean isCurrent(Account account) {
        UUID uuid = account.uuid();
        Session session = getCurrentSession();
        return uuid != null && session != null && uuid.equals(session.getUuidOrNull());
    }

    public static boolean applyAccount(Account account) {
        UUID uuid = account.uuid();
        String accessToken = account.getAccessToken();
        if (uuid == null || accessToken == null || accessToken.isBlank()) {
            return false;
        }
        setSession(new Session(account.getName(), uuid, accessToken, Optional.empty(), Optional.empty()));
        return true;
    }

    /** Keeps the live session in sync when the account it belongs to gets refreshed. */
    public static void refreshCurrent(Account account) {
        Session current = getCurrentSession();
        if (current == null || !isCurrent(account)) {
            return;
        }
        UUID uuid = account.uuid();
        if (uuid == null) {
            return;
        }
        String accessToken = account.getAccessToken();
        if (accessToken == null || accessToken.isBlank()) {
            return;
        }
        setSession(new Session(account.getName(), uuid, accessToken, current.getXuid(), current.getClientId()));
    }

    private static void setSession(Session session) {
        ((MinecraftClientSessionAccessor) (Object) MinecraftClient.getInstance())
            .tokenlogin$setSession(session);
    }
}
