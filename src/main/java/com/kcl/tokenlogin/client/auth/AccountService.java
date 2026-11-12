package com.kcl.tokenlogin.client.auth;

import com.kcl.tokenlogin.TokenLogin;
import com.kcl.tokenlogin.client.config.TokenLoginConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Importing, refreshing and logging in with accounts. Every callback runs on the
 * client thread; the network work happens on a worker thread.
 */
public final class AccountService {
    /** How many failed refreshes in a row kill a refresh token. */
    public static final int MAX_REFRESH_FAILURES = 3;

    /** Refresh an access token when it has less than this left. */
    public static final long REFRESH_AHEAD_MS = 10 * 60 * 1000L;
    /** Wait this long before retrying an account that just failed. */
    private static final long RETRY_INTERVAL_MS = 3 * 60 * 1000L;
    private static final long CHECK_INTERVAL_MS = 20 * 1000L;

    private static long lastCheck;

    private AccountService() {
    }

    public record RefreshResult(boolean ok, MsAuth.Failure failure, int failures) {
    }

    public record ImportResult(boolean ok, MsAuth.Failure failure, Account account) {
    }

    public record LoginResult(boolean ok, Text message) {
    }

    public static Text failureText(MsAuth.Failure failure) {
        if (failure == null) {
            return Text.translatable("tokenlogin.error.unknown", "");
        }
        String detail = failure.detail() == null ? "" : failure.detail().trim();
        if (detail.isEmpty()) {
            return Text.translatable(failure.messageKey());
        }
        // Only the "invalid refresh token" message has a variant that shows the
        // server's reason; the other keys either take a parameter already or are
        // plain sentences.
        if (failure.kind() == MsAuth.Kind.INVALID_REFRESH
            && "tokenlogin.error.invalid_refresh".equals(failure.messageKey())) {
            return Text.translatable("tokenlogin.error.invalid_refresh_detail", detail);
        }
        return Text.translatable(failure.messageKey(), detail);
    }

    /** Called from the client tick; refreshes accounts that are about to expire. */
    public static void tick() {
        if (!TokenLoginConfig.isAutoRefresh()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastCheck < CHECK_INTERVAL_MS) {
            return;
        }
        lastCheck = now;
        for (Account account : AccountStore.get().snapshot()) {
            if (account.isRefreshing() || account.isRefreshInvalid()) {
                continue;
            }
            String refreshToken = account.getRefreshToken();
            if (refreshToken == null || refreshToken.isBlank()) {
                continue;
            }
            if (account.getAccessExpiresAt() - now > REFRESH_AHEAD_MS) {
                continue;
            }
            if (now - account.getLastRefreshAttempt() < RETRY_INTERVAL_MS) {
                continue;
            }
            refresh(account, null);
        }
    }

    public static void refresh(Account account, Consumer<RefreshResult> callback) {
        if (account.isRefreshing()) {
            if (callback != null) {
                callback.accept(new RefreshResult(false, null, account.getFailedRefreshes()));
            }
            return;
        }
        String refreshToken = account.getRefreshToken();
        if (refreshToken == null || refreshToken.isBlank()) {
            markFailure(account, new MsAuth.Failure(
                MsAuth.Kind.INVALID_REFRESH, "tokenlogin.error.invalid_refresh", null), callback);
            return;
        }
        account.setRefreshing(true);
        account.setLastRefreshAttempt(System.currentTimeMillis());

        MinecraftClient client = MinecraftClient.getInstance();
        CompletableFuture.supplyAsync(() -> MsAuth.refresh(refreshToken))
            .thenAccept(result -> client.execute(() -> applyRefresh(account, result, callback)))
            .exceptionally(throwable -> {
                client.execute(() -> {
                    account.setRefreshing(false);
                    if (callback != null) {
                        callback.accept(new RefreshResult(false,
                            new MsAuth.Failure(MsAuth.Kind.NETWORK, "tokenlogin.error.network", null),
                            account.getFailedRefreshes()));
                    }
                });
                return null;
            });
    }

    private static void applyRefresh(Account account, MsAuth.Result result, Consumer<RefreshResult> callback) {
        account.setRefreshing(false);
        if (result instanceof MsAuth.Success success) {
            account.setProfileId(success.profileId());
            account.setName(success.profileName());
            account.setAccessToken(success.accessToken());
            account.setAccessExpiresAt(success.expiresAt());
            account.setRefreshToken(success.refreshToken());
            account.setFailedRefreshes(0);
            account.setRefreshInvalid(false);
            AccountStore.get().save();
            UserManager.refreshCurrent(account);
            if (callback != null) {
                callback.accept(new RefreshResult(true, null, 0));
            }
            return;
        }
        if (result instanceof MsAuth.Failure failure) {
            markFailure(account, failure, callback);
        }
    }

    private static void markFailure(Account account, MsAuth.Failure failure, Consumer<RefreshResult> callback) {
        int failures = account.getFailedRefreshes() + 1;
        account.setFailedRefreshes(failures);
        if (failures >= MAX_REFRESH_FAILURES) {
            account.setRefreshInvalid(true);
        }
        account.setRefreshing(false);
        AccountStore.get().save();
        if (callback != null) {
            callback.accept(new RefreshResult(false, failure, failures));
        }
    }

    public static void importToken(String raw, Consumer<ImportResult> callback) {
        String refreshToken = TokenParser.extract(raw);
        if (refreshToken == null) {
            TokenLogin.LOGGER.warn("Import: pasted text ({} chars) does not contain a refresh token",
                raw == null ? 0 : raw.length());
            callback.accept(new ImportResult(false,
                new MsAuth.Failure(MsAuth.Kind.INVALID_REFRESH, "tokenlogin.error.no_token", null), null));
            return;
        }
        TokenLogin.LOGGER.info("Import: extracted a refresh token of {} chars", refreshToken.length());
        MinecraftClient client = MinecraftClient.getInstance();
        CompletableFuture.supplyAsync(() -> MsAuth.refresh(refreshToken))
            .thenAccept(result -> client.execute(() -> {
                if (result instanceof MsAuth.Success success) {
                    Account account = AccountStore.get().find(success.profileId());
                    if (account == null) {
                        account = new Account();
                    }
                    account.setProfileId(success.profileId());
                    account.setName(success.profileName());
                    account.setRefreshToken(success.refreshToken());
                    account.setAccessToken(success.accessToken());
                    account.setAccessExpiresAt(success.expiresAt());
                    account.setFailedRefreshes(0);
                    account.setRefreshInvalid(false);
                    account.setImportedAt(System.currentTimeMillis());
                    account.setLastRefreshAttempt(System.currentTimeMillis());
                    account.setRefreshing(false);
                    AccountStore.get().put(account);
                    TokenLogin.LOGGER.info("Import: stored account {} ({})",
                        success.profileName(), success.profileId());
                    callback.accept(new ImportResult(true, null, account));
                } else {
                    MsAuth.Failure failure = (MsAuth.Failure) result;
                    TokenLogin.LOGGER.warn("Import failed: {} {}", failure.kind(), failure.detail());
                    callback.accept(new ImportResult(false, failure, null));
                }
            }))
            .exceptionally(throwable -> {
                client.execute(() -> callback.accept(new ImportResult(false,
                    new MsAuth.Failure(MsAuth.Kind.NETWORK, "tokenlogin.error.network", null), null)));
                return null;
            });
    }

    public static void login(Account account, Consumer<LoginResult> callback) {
        if (account.hasUsableAccessToken()) {
            callback.accept(loginNow(account));
            return;
        }
        refresh(account, result -> {
            if (result.ok()) {
                callback.accept(loginNow(account));
            } else {
                callback.accept(new LoginResult(false, failureText(result.failure())));
            }
        });
    }

    private static LoginResult loginNow(Account account) {
        if (UserManager.applyAccount(account)) {
            return new LoginResult(true, Text.translatable("tokenlogin.status.logged_in", account.getName()));
        }
        return new LoginResult(false, Text.translatable("tokenlogin.status.session_failed"));
    }
}
