package com.kcl.tokenlogin.client.auth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * Microsoft / Xbox Live / Minecraft authentication chain.
 *
 * <p>A Microsoft refresh token is exchanged for a Microsoft access token, which
 * is then used to obtain an Xbox Live token, an XSTS token and finally a
 * Minecraft access token (plus the profile of the account).</p>
 */
public final class MsAuth {
    private static final String OAUTH_TOKEN_URL = "https://login.live.com/oauth20_token.srf";
    private static final String OAUTH_V2_URL =
        "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
    private static final String XBL_URL = "https://user.auth.xboxlive.com/user/authenticate";
    private static final String XSTS_URL = "https://xsts.auth.xboxlive.com/xsts/authorize";
    private static final String MC_LOGIN_URL = "https://api.minecraftservices.com/authentication/login_with_xbox";
    private static final String PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile";

    /** Client id of the official Minecraft launcher, required by the legacy live.com flow. */
    private static final String CLIENT_ID = "00000000402b5328";
    private static final String SCOPE = "service::user.auth.xboxlive.com::MBI_SSL";
    private static final String SCOPE_V2 = "XboxLive.signin offline_access";
    private static final String USER_AGENT = "TokenLogin/0.1.0 (Fabric 1.21.11)";
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(TIMEOUT)
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    private MsAuth() {
    }

    public enum Kind {
        /** The refresh token itself is dead and the user has to import a new one. */
        INVALID_REFRESH,
        /** No connection / the auth server could not be reached. */
        NETWORK,
        /** The Microsoft account exists but cannot be used for Minecraft. */
        ACCOUNT,
        UNKNOWN
    }

    public sealed interface Result permits Success, Failure {
    }

    public record Success(String accessToken, String refreshToken, long expiresAt,
                          String profileId, String profileName) implements Result {
    }

    public record Failure(Kind kind, String messageKey, String detail) implements Result {
    }

    public static Result refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return new Failure(Kind.INVALID_REFRESH, "tokenlogin.error.invalid_refresh", null);
        }
        try {
            // Step 1: refresh token -> Microsoft access token.
            Object exchanged = exchangeRefreshToken(refreshToken);
            if (exchanged instanceof Failure failure) {
                return failure;
            }
            MsaTokens msa = (MsaTokens) exchanged;
            String msAccessToken = msa.accessToken();
            String nextRefreshToken = msa.refreshToken();

            // Step 2: Microsoft access token -> Xbox Live token.
            JsonObject properties = new JsonObject();
            properties.addProperty("AuthMethod", "RPS");
            properties.addProperty("SiteName", "user.auth.xboxlive.com");
            properties.addProperty("RpsTicket", "d=" + msAccessToken);
            JsonObject xblRequest = new JsonObject();
            xblRequest.add("Properties", properties);
            xblRequest.addProperty("RelyingParty", "http://auth.xboxlive.com");
            xblRequest.addProperty("TokenType", "JWT");
            HttpResponse<String> xblResponse = sendJson(XBL_URL, xblRequest.toString());
            if (xblResponse.statusCode() != 200) {
                return xboxFailure(xblResponse);
            }
            JsonObject xbl = parseObject(xblResponse.body());
            String xblToken = string(xbl, "Token");
            String userHash = readUserHash(xbl);
            if (xblToken == null || userHash == null) {
                return new Failure(Kind.ACCOUNT, "tokenlogin.error.xbox", null);
            }

            // Step 3: Xbox Live token -> XSTS token.
            JsonObject xstsProperties = new JsonObject();
            xstsProperties.addProperty("SandboxId", "RETAIL");
            JsonArray userTokens = new JsonArray();
            userTokens.add(xblToken);
            xstsProperties.add("UserTokens", userTokens);
            JsonObject xstsRequest = new JsonObject();
            xstsRequest.add("Properties", xstsProperties);
            xstsRequest.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
            xstsRequest.addProperty("TokenType", "JWT");
            HttpResponse<String> xstsResponse = sendJson(XSTS_URL, xstsRequest.toString());
            if (xstsResponse.statusCode() != 200) {
                return xboxFailure(xstsResponse);
            }
            JsonObject xsts = parseObject(xstsResponse.body());
            String xstsToken = string(xsts, "Token");
            String xstsUserHash = readUserHash(xsts);
            if (xstsToken == null) {
                return new Failure(Kind.ACCOUNT, "tokenlogin.error.xbox", null);
            }
            if (xstsUserHash != null) {
                userHash = xstsUserHash;
            }

            // Step 4: XSTS token -> Minecraft access token.
            JsonObject mcRequest = new JsonObject();
            mcRequest.addProperty("identityToken", "XBL3.0 x=" + userHash + ";" + xstsToken);
            HttpResponse<String> mcResponse = sendJson(MC_LOGIN_URL, mcRequest.toString());
            if (mcResponse.statusCode() != 200) {
                return new Failure(Kind.ACCOUNT, "tokenlogin.error.no_minecraft",
                    "HTTP " + mcResponse.statusCode());
            }
            JsonObject mc = parseObject(mcResponse.body());
            String accessToken = string(mc, "access_token");
            if (accessToken == null) {
                return new Failure(Kind.UNKNOWN, "tokenlogin.error.http", "missing access_token");
            }
            long expiresIn = 86_400L;
            if (mc.has("expires_in") && mc.get("expires_in").isJsonPrimitive()) {
                try {
                    expiresIn = mc.get("expires_in").getAsLong();
                } catch (NumberFormatException ignored) {
                    expiresIn = 86_400L;
                }
            }
            if (expiresIn <= 0L) {
                expiresIn = 86_400L;
            }

            // Step 5: fetch the profile (id + name) that belongs to the token.
            HttpResponse<String> profileResponse = send(HttpRequest.newBuilder(URI.create(PROFILE_URL))
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .GET()
                .build());
            if (profileResponse.statusCode() == 404) {
                return new Failure(Kind.ACCOUNT, "tokenlogin.error.no_minecraft", null);
            }
            if (profileResponse.statusCode() != 200) {
                return new Failure(Kind.UNKNOWN, "tokenlogin.error.http",
                    "HTTP " + profileResponse.statusCode());
            }
            JsonObject profile = parseObject(profileResponse.body());
            String profileId = string(profile, "id");
            String profileName = string(profile, "name");
            if (profileId == null || profileName == null) {
                return new Failure(Kind.UNKNOWN, "tokenlogin.error.http", "missing profile");
            }

            long expiresAt = System.currentTimeMillis() + expiresIn * 1000L;
            return new Success(accessToken, nextRefreshToken, expiresAt,
                profileId.replace("-", "").toLowerCase(java.util.Locale.ROOT), profileName);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Failure(Kind.NETWORK, "tokenlogin.error.network", null);
        } catch (Exception e) {
            return new Failure(Kind.NETWORK, "tokenlogin.error.network", e.getMessage());
        }
    }

    private record MsaTokens(String accessToken, String refreshToken) {
    }

    /**
     * Exchanges a Microsoft refresh token for a Microsoft access token.
     *
     * <p>Two endpoints are tried in turn: the legacy {@code login.live.com} one
     * used by the official launcher, and the v2 {@code consumers} one. Tokens
     * issued by newer sign-in flows are rejected by the legacy endpoint with
     * {@code invalid_grant} even though they are perfectly valid, so the second
     * attempt is what makes those imports work.</p>
     *
     * @return an {@link MsaTokens} on success, a {@link Failure} otherwise
     */
    private static Object exchangeRefreshToken(String refreshToken) {
        Failure last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            boolean v2 = attempt == 1;
            String url = v2 ? OAUTH_V2_URL : OAUTH_TOKEN_URL;
            String scope = v2 ? SCOPE_V2 : SCOPE;
            String form = "client_id=" + encode(CLIENT_ID)
                + "&grant_type=refresh_token"
                + "&refresh_token=" + encode(refreshToken)
                + "&scope=" + encode(scope);
            try {
                HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(url))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Accept", "application/json")
                    .header("User-Agent", USER_AGENT)
                    .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                    .build());
                if (response.statusCode() == 200) {
                    JsonObject json = parseObject(response.body());
                    String accessToken = string(json, "access_token");
                    if (accessToken != null) {
                        String next = string(json, "refresh_token");
                        if (next == null || next.isBlank()) {
                            next = refreshToken;
                        }
                        return new MsaTokens(accessToken, next);
                    }
                    last = new Failure(Kind.UNKNOWN, "tokenlogin.error.http", "missing access_token");
                    continue;
                }
                String error = readErrorCode(response.body());
                String detail = readErrorDetail(response.body());
                if ("invalid_grant".equals(error) || "invalid_request".equals(error)
                    || "interaction_required".equals(error)
                    || response.statusCode() == 400 || response.statusCode() == 401) {
                    last = new Failure(Kind.INVALID_REFRESH, "tokenlogin.error.invalid_refresh", detail);
                } else {
                    last = new Failure(Kind.UNKNOWN, "tokenlogin.error.http",
                        "HTTP " + response.statusCode() + (detail == null ? "" : " " + detail));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new Failure(Kind.NETWORK, "tokenlogin.error.network", null);
            } catch (Exception e) {
                last = new Failure(Kind.NETWORK, "tokenlogin.error.network", e.getMessage());
            }
        }
        return last == null
            ? new Failure(Kind.INVALID_REFRESH, "tokenlogin.error.invalid_refresh", null)
            : last;
    }

    private static Failure xboxFailure(HttpResponse<String> response) {
        String xerr = null;
        try {
            JsonObject body = parseObject(response.body());
            if (body.has("XErr") && body.get("XErr").isJsonPrimitive()) {
                xerr = body.get("XErr").getAsString();
            }
        } catch (Exception ignored) {
            // Not a JSON error body.
        }
        if (xerr != null) {
            return switch (xerr) {
                case "2148916233" -> new Failure(Kind.ACCOUNT, "tokenlogin.error.xbox_no_account", xerr);
                case "2148916235" -> new Failure(Kind.ACCOUNT, "tokenlogin.error.xbox_region", xerr);
                case "2148916238" -> new Failure(Kind.ACCOUNT, "tokenlogin.error.xbox_child", xerr);
                default -> new Failure(Kind.ACCOUNT, "tokenlogin.error.xbox", "XErr " + xerr);
            };
        }
        return new Failure(Kind.ACCOUNT, "tokenlogin.error.xbox", "HTTP " + response.statusCode());
    }

    private static String readUserHash(JsonObject json) {
        if (!json.has("DisplayClaims") || !json.get("DisplayClaims").isJsonObject()) {
            return null;
        }
        JsonObject claims = json.getAsJsonObject("DisplayClaims");
        if (!claims.has("xui") || !claims.get("xui").isJsonArray()) {
            return null;
        }
        JsonArray xui = claims.getAsJsonArray("xui");
        if (xui.isEmpty() || !xui.get(0).isJsonObject()) {
            return null;
        }
        return string(xui.get(0).getAsJsonObject(), "uhs");
    }

    private static String readErrorCode(String body) {
        try {
            JsonObject json = parseObject(body);
            if (json.has("error") && json.get("error").isJsonPrimitive()) {
                return json.get("error").getAsString();
            }
        } catch (Exception ignored) {
            // Not JSON.
        }
        return null;
    }

    /** Short human readable reason from an OAuth error body, for the tooltip. */
    private static String readErrorDetail(String body) {
        try {
            JsonObject json = parseObject(body);
            String detail = string(json, "error_description");
            if (detail == null) {
                detail = string(json, "suberror");
            }
            if (detail == null) {
                return null;
            }
            // The descriptions are long prose; the AADSTS code at the front is
            // the part that actually identifies the problem.
            String trimmed = detail.replace('\n', ' ').replace('\r', ' ').trim();
            if (trimmed.length() > 64) {
                trimmed = trimmed.substring(0, 64) + "...";
            }
            return trimmed;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static HttpResponse<String> sendJson(String url, String body) throws Exception {
        return send(HttpRequest.newBuilder(URI.create(url))
            .timeout(TIMEOUT)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build());
    }

    private static HttpResponse<String> send(HttpRequest request) throws Exception {
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static JsonObject parseObject(String body) {
        JsonElement element = JsonParser.parseString(body);
        return element.getAsJsonObject();
    }

    private static String string(JsonObject object, String key) {
        if (object == null || !object.has(key)) {
            return null;
        }
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return null;
        }
        String value = element.getAsString();
        return value == null || value.isBlank() ? null : value;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** Decodes a base64 textures property; shared with the avatar loader. */
    public static String decodeBase64(String value) {
        if (value == null) {
            return null;
        }
        try {
            return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
