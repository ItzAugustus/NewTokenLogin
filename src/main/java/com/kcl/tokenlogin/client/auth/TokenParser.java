package com.kcl.tokenlogin.client.auth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pulls a refresh token out of whatever the user pasted: a bare token, a
 * launcher account file, an OAuth response, or pretty printed JSON.
 *
 * <p>Microsoft refresh tokens are not URL-safe identifiers: they contain
 * characters such as {@code !}, {@code *}, {@code %}, {@code #} and can be well
 * over 1000 characters long. An earlier version of this class only accepted
 * {@code [A-Za-z0-9._~+/=-]}, which silently cut a real token short or rejected
 * it completely. Everything below therefore treats "anything that is not
 * whitespace or JSON punctuation" as part of a token.</p>
 */
public final class TokenParser {
    /** {@code refresh_token: ...}, {@code "refreshToken":"..."}, {@code refresh-token = ...}. */
    private static final Pattern KEY_VALUE = Pattern.compile(
        "(?i)[\"']?(?:(?:ms|msa|microsoft|xbl|mc)[\\s_\\-]?)?(?:refresh[\\s_\\-]?token)[\"']?"
            + "\\s*[:=]\\s*[\"']?([^\\s\"',;}\\]]{16,})");

    /** One whole line that is nothing but a token (quotes allowed around it). */
    private static final Pattern BARE_LINE = Pattern.compile(
        "[\"']?([^\\s\"',;{}\\[\\]]{20,})[\"']?");

    private TokenParser() {
    }

    /** @return the refresh token, or {@code null} when nothing usable was found. */
    public static String extract(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        if (text.isEmpty()) {
            return null;
        }

        // 1. A JSON document (launcher file, OAuth response, pretty printed blob),
        //    possibly without the leading brace because the user copied a fragment.
        String fromJson = fromJson(text);
        if (fromJson != null) {
            return fromJson;
        }

        // 2. Explicit "refresh token: xxx" style key/value pairs.
        Matcher keyValue = KEY_VALUE.matcher(text);
        if (keyValue.find()) {
            String cleaned = clean(keyValue.group(1));
            if (cleaned != null) {
                return cleaned;
            }
        }

        // 3. A token on its own line, possibly wrapped in quotes.
        for (String line : text.split("\\R")) {
            String cleaned = clean(line);
            if (cleaned == null || cleaned.length() < 20) {
                continue;
            }
            if (BARE_LINE.matcher(line.trim()).matches()) {
                return cleaned;
            }
        }

        // 4. Last resort: take the longest token-shaped run anywhere in the text.
        String run = longestRun(text);
        return run == null ? null : clean(run);
    }

    private static String fromJson(String text) {
        int brace = text.indexOf('{');
        int bracket = text.indexOf('[');
        int start = brace < 0 ? bracket : (bracket < 0 ? brace : Math.min(brace, bracket));
        if (start < 0) {
            return null;
        }
        String candidate = text.substring(start).trim();
        try {
            JsonElement root = JsonParser.parseString(candidate);
            String found = searchJson(root);
            return found == null ? null : clean(found);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String longestRun(String text) {
        String best = null;
        int index = 0;
        int length = text.length();
        while (index < length) {
            if (isTokenChar(text.charAt(index))) {
                int end = index;
                while (end < length && isTokenChar(text.charAt(end))) {
                    end++;
                }
                if (end - index >= 20) {
                    String run = text.substring(index, end);
                    if (best == null || run.length() > best.length()) {
                        best = run;
                    }
                }
                index = end;
            } else {
                index++;
            }
        }
        return best;
    }

    private static boolean isTokenChar(char c) {
        return !Character.isWhitespace(c) && c != '"' && c != '\'' && c != ',' && c != ';'
            && c != '{' && c != '}' && c != '[' && c != ']' && c != '\\' && c != '<' && c != '>';
    }

    /** Trims quotes, punctuation glued to the end, and rejects obvious non-tokens. */
    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String result = value.trim();
        while (!result.isEmpty()) {
            char first = result.charAt(0);
            char last = result.charAt(result.length() - 1);
            if (first == '"' || first == '\'' || first == ',' || first == ';') {
                result = result.substring(1).trim();
            } else if (last == '"' || last == '\'' || last == ',' || last == ';') {
                result = result.substring(0, result.length() - 1).trim();
            } else {
                break;
            }
        }
        if (result.length() < 16) {
            return null;
        }
        // URLs and file paths are never refresh tokens.
        String lower = result.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.contains("://")) {
            return null;
        }
        for (int i = 0; i < result.length(); i++) {
            if (Character.isWhitespace(result.charAt(i))) {
                return null;
            }
        }
        return result;
    }

    private static String searchJson(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                if (isRefreshKey(entry.getKey()) && isString(entry.getValue())) {
                    return entry.getValue().getAsString();
                }
            }
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                String nested = searchJson(entry.getValue());
                if (nested != null) {
                    return nested;
                }
            }
            return null;
        }
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (JsonElement child : array) {
                String nested = searchJson(child);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    private static boolean isRefreshKey(String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.toLowerCase(Locale.ROOT)
            .replace("_", "").replace("-", "").replace(" ", "");
        return "refreshtoken".equals(normalized)
            || "msrefreshtoken".equals(normalized)
            || "msarefreshtoken".equals(normalized)
            || "microsoftrefreshtoken".equals(normalized)
            || "xblrefreshtoken".equals(normalized);
    }

    private static boolean isString(JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) {
            return false;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        return primitive.isString() && !primitive.getAsString().isBlank();
    }
}
