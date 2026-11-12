package com.kcl.tokenlogin.client.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kcl.tokenlogin.TokenLogin;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Client settings stored in {@code config/tokenlogin.json}. */
public final class TokenLoginConfig {
    public enum ButtonPosition {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT,
        NONE;

        private static final ButtonPosition[] VALUES = values();

        public ButtonPosition next() {
            return VALUES[(ordinal() + 1) % VALUES.length];
        }

        public String translationKey() {
            return "tokenlogin.position." + name().toLowerCase(Locale.ROOT);
        }

        public static ButtonPosition parse(String value, ButtonPosition fallback) {
            if (value == null) {
                return fallback;
            }
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return fallback;
            }
        }
    }

    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("tokenlogin.json");

    private static ButtonPosition buttonPosition = ButtonPosition.TOP_RIGHT;
    /** Keep access tokens alive without the user clicking anything. */
    private static boolean autoRefresh = true;
    private static boolean loaded;

    private TokenLoginConfig() {
    }

    public static synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        if (!Files.exists(FILE)) {
            return;
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8))
                .getAsJsonObject();
            if (root.has("buttonPosition") && root.get("buttonPosition").isJsonPrimitive()) {
                buttonPosition = ButtonPosition.parse(root.get("buttonPosition").getAsString(), buttonPosition);
            }
            if (root.has("autoRefresh") && root.get("autoRefresh").isJsonPrimitive()) {
                autoRefresh = root.get("autoRefresh").getAsBoolean();
            }
        } catch (Exception e) {
            TokenLogin.LOGGER.error("Failed to read config from {}", FILE, e);
        }
    }

    public static synchronized ButtonPosition getButtonPosition() {
        return buttonPosition;
    }

    public static synchronized void setButtonPosition(ButtonPosition position) {
        buttonPosition = position;
        save();
    }

    public static synchronized ButtonPosition cycleButtonPosition() {
        buttonPosition = buttonPosition.next();
        save();
        return buttonPosition;
    }

    public static synchronized boolean isAutoRefresh() {
        return autoRefresh;
    }

    public static synchronized void setAutoRefresh(boolean value) {
        autoRefresh = value;
        save();
    }

    public static synchronized boolean toggleAutoRefresh() {
        autoRefresh = !autoRefresh;
        save();
        return autoRefresh;
    }

    private static void save() {
        try {
            JsonObject root = new JsonObject();
            root.addProperty("buttonPosition", buttonPosition.name());
            root.addProperty("autoRefresh", autoRefresh);
            Path parent = FILE.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(FILE, root.toString(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            TokenLogin.LOGGER.error("Failed to save config to {}", FILE, e);
        }
    }
}
