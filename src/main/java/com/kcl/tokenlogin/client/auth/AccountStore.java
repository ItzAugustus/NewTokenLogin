package com.kcl.tokenlogin.client.auth;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kcl.tokenlogin.TokenLogin;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistent list of imported accounts. All access happens on the client
 * thread; the file itself is small enough to write synchronously.
 */
public final class AccountStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final AccountStore INSTANCE = new AccountStore();

    private final List<Account> accounts = new ArrayList<>();
    private final Path file;
    private boolean loaded;

    private AccountStore() {
        this.file = FabricLoader.getInstance().getConfigDir().resolve("tokenlogin-accounts.json");
    }

    public static AccountStore get() {
        return INSTANCE;
    }

    public synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        if (!Files.exists(file)) {
            return;
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                .getAsJsonObject();
            JsonElement list = root.get("accounts");
            if (list == null || !list.isJsonArray()) {
                return;
            }
            for (JsonElement element : list.getAsJsonArray()) {
                if (!element.isJsonObject()) {
                    continue;
                }
                Account account = GSON.fromJson(element, Account.class);
                if (account != null && account.normalizedId() != null) {
                    // Older builds did not store the import time; start counting
                    // from now instead of showing "imported 55 years ago".
                    if (account.getImportedAt() <= 0L) {
                        account.setImportedAt(System.currentTimeMillis());
                    }
                    accounts.add(account);
                }
            }
        } catch (Exception e) {
            TokenLogin.LOGGER.error("Failed to read accounts from {}", file, e);
        }
    }

    public synchronized void save() {
        try {
            JsonObject root = new JsonObject();
            JsonArray list = new JsonArray();
            for (Account account : accounts) {
                list.add(GSON.toJsonTree(account));
            }
            root.add("accounts", list);
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            // Write to a temporary file and swap it in, so a crash halfway
            // through a write can never leave a half written account list.
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temp, GSON.toJson(root), StandardCharsets.UTF_8);
            try {
                Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            TokenLogin.LOGGER.error("Failed to save accounts to {}", file, e);
        }
    }

    public synchronized List<Account> snapshot() {
        return new ArrayList<>(accounts);
    }

    public synchronized Account find(String profileId) {
        if (profileId == null) {
            return null;
        }
        String wanted = profileId.replace("-", "").toLowerCase(java.util.Locale.ROOT);
        for (Account account : accounts) {
            if (wanted.equals(account.normalizedId())) {
                return account;
            }
        }
        return null;
    }

    public synchronized void put(Account account) {
        Account existing = find(account.normalizedId());
        if (existing != null) {
            accounts.remove(existing);
        }
        accounts.add(account);
        save();
    }

    public synchronized void remove(Account account) {
        accounts.remove(account);
        save();
    }

    public synchronized int size() {
        return accounts.size();
    }
}
