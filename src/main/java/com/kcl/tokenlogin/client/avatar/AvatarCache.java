package com.kcl.tokenlogin.client.avatar;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kcl.tokenlogin.TokenLogin;
import com.kcl.tokenlogin.client.auth.MsAuth;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Downloads and caches full player skins as dynamic textures.
 *
 * <p>A head icon is not cropped here any more: the complete 64x64 skin is kept
 * and drawn with {@code PlayerSkinDrawer}, which is exactly what the vanilla
 * server list does. That keeps the face, the hat layer and the slim/classic
 * proportions right instead of showing a stretched 8x8 blob.</p>
 *
 * <p>The official session server is used first so no third party is needed;
 * {@code mc-heads.net} is only a fallback. Skins are cached under
 * {@code config/tokenlogin/skins}.</p>
 */
public final class AvatarCache {
    private static final Path DIR = FabricLoader.getInstance().getConfigDir()
        .resolve("tokenlogin").resolve("skins");
    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final String USER_AGENT = "TokenLogin/0.1.0 (Fabric 1.21.11)";
    private static final long RETRY_DELAY_MS = 120_000L;
    /** Width/height the vanilla skin drawer assumes. */
    private static final int SKIN_WIDTH = 64;
    private static final int SKIN_HEIGHT = 64;

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(TIMEOUT)
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    private static final Map<String, Identifier> READY = new HashMap<>();
    private static final Set<String> PENDING = new HashSet<>();
    private static final Map<String, Long> RETRY_AFTER = new HashMap<>();

    private AvatarCache() {
    }

    /** Main thread only. Returns {@code null} while loading or when unavailable. */
    public static Identifier get(String profileId) {
        if (profileId == null) {
            return null;
        }
        String id = profileId.replace("-", "").toLowerCase(Locale.ROOT);
        if (id.length() != 32) {
            return null;
        }
        Identifier cached = READY.get(id);
        if (cached != null) {
            return cached;
        }
        Long retry = RETRY_AFTER.get(id);
        if (retry != null && System.currentTimeMillis() < retry) {
            return null;
        }
        if (!PENDING.add(id)) {
            return null;
        }
        CompletableFuture.supplyAsync(() -> loadSkin(id))
            .thenAccept(image -> MinecraftClient.getInstance().execute(() -> register(id, image)));
        return null;
    }

    private static void register(String id, NativeImage image) {
        PENDING.remove(id);
        if (image == null) {
            RETRY_AFTER.put(id, System.currentTimeMillis() + RETRY_DELAY_MS);
            return;
        }
        try {
            Identifier identifier = Identifier.of(TokenLogin.MOD_ID, "skins/" + id);
            MinecraftClient.getInstance().getTextureManager()
                .registerTexture(identifier, new NativeImageBackedTexture(() -> "tokenlogin skin " + id, image));
            READY.put(id, identifier);
        } catch (Exception e) {
            image.close();
            RETRY_AFTER.put(id, System.currentTimeMillis() + RETRY_DELAY_MS);
            TokenLogin.LOGGER.warn("Failed to register skin texture for {}", id, e);
        }
    }

    private static NativeImage loadSkin(String id) {
        try {
            Path cached = DIR.resolve(id + ".png");
            if (Files.exists(cached)) {
                NativeImage fromDisk = readCached(cached);
                if (fromDisk != null) {
                    return fromDisk;
                }
                // Corrupted or from an old format: download again.
                try {
                    Files.deleteIfExists(cached);
                } catch (Exception ignored) {
                    // Not fatal, the download below can still succeed.
                }
            }
            NativeImage skin = downloadOfficialSkin(id);
            if (skin == null) {
                skin = downloadFromMcHeads(id);
            }
            if (skin != null) {
                save(skin, cached);
            }
            return skin;
        } catch (Exception e) {
            TokenLogin.LOGGER.debug("Could not load skin for {}", id, e);
            return null;
        }
    }

    private static NativeImage readCached(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            return normalize(NativeImage.read(in));
        } catch (Exception e) {
            return null;
        }
    }

    /** The Mojang session server hands out the signed skin URL. */
    private static NativeImage downloadOfficialSkin(String id) {
        try {
            byte[] profileBytes = httpGet("https://sessionserver.mojang.com/session/minecraft/profile/" + id);
            if (profileBytes == null) {
                return null;
            }
            JsonObject profile = JsonParser.parseString(new String(profileBytes, StandardCharsets.UTF_8))
                .getAsJsonObject();
            String skinUrl = skinUrl(profile);
            if (skinUrl == null) {
                return null;
            }
            byte[] skinBytes = httpGet(skinUrl);
            if (skinBytes == null) {
                return null;
            }
            return normalize(NativeImage.read(skinBytes));
        } catch (Exception e) {
            TokenLogin.LOGGER.debug("Session server skin lookup failed for {}", id, e);
            return null;
        }
    }

    private static String skinUrl(JsonObject profile) {
        if (!profile.has("properties") || !profile.get("properties").isJsonArray()) {
            return null;
        }
        JsonArray properties = profile.getAsJsonArray("properties");
        for (JsonElement element : properties) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject property = element.getAsJsonObject();
            if (!property.has("name") || !"textures".equals(property.get("name").getAsString())) {
                continue;
            }
            String value = property.has("value") ? property.get("value").getAsString() : null;
            String decoded = MsAuth.decodeBase64(value);
            if (decoded == null) {
                continue;
            }
            try {
                JsonObject textures = JsonParser.parseString(decoded).getAsJsonObject()
                    .getAsJsonObject("textures");
                if (textures != null && textures.has("SKIN")) {
                    JsonObject skin = textures.getAsJsonObject("SKIN");
                    if (skin.has("url")) {
                        return skin.get("url").getAsString();
                    }
                }
            } catch (Exception ignored) {
                // Try the next property.
            }
        }
        return null;
    }

    private static NativeImage downloadFromMcHeads(String id) {
        try {
            byte[] bytes = httpGet("https://mc-heads.net/skin/" + id);
            if (bytes == null) {
                return null;
            }
            return normalize(NativeImage.read(bytes));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Brings whatever the server returned into the 64x64 layout the vanilla skin
     * drawer samples: old skins are 64x32 (or 32x32) and only cover the top half.
     */
    private static NativeImage normalize(NativeImage image) {
        if (image == null) {
            return null;
        }
        int width = image.getWidth();
        int height = image.getHeight();
        if (width == SKIN_WIDTH && height == SKIN_HEIGHT) {
            return image;
        }
        if (width != SKIN_WIDTH && width != 32) {
            image.close();
            return null;
        }
        NativeImage out = new NativeImage(SKIN_WIDTH, SKIN_HEIGHT, true);
        try {
            int copyWidth = Math.min(width, SKIN_WIDTH);
            int copyHeight = Math.min(height, 32);
            for (int y = 0; y < copyHeight; y++) {
                for (int x = 0; x < copyWidth; x++) {
                    out.setColorArgb(x, y, image.getColorArgb(x, y));
                }
            }
            return out;
        } catch (Exception e) {
            out.close();
            return null;
        } finally {
            image.close();
        }
    }

    private static byte[] httpGet(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();
            HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                return null;
            }
            byte[] body = response.body();
            return body == null || body.length == 0 ? null : body;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static void save(NativeImage image, Path file) {
        try {
            Files.createDirectories(DIR);
            image.writeTo(file);
        } catch (Exception e) {
            TokenLogin.LOGGER.debug("Failed to cache skin at {}", file, e);
        }
    }
}
