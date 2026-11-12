package com.kcl.tokenlogin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared constants for TokenLogin. The mod is client only, so there is no
 * common initializer; the client entrypoint lives in
 * {@link com.kcl.tokenlogin.client.TokenLoginClient}.
 */
public final class TokenLogin {
    public static final String MOD_ID = "tokenlogin";
    public static final String MOD_NAME = "TokenLogin";
    public static final String AUTHOR = "ItzAugustus";
    /**
     * Fallback only: the version shown in game comes from {@code fabric.mod.json},
     * which Gradle fills in from {@code gradle.properties}. Keep this in sync with
     * the base version and let the build stamp the {@code -rN} suffix.
     */
    public static final String VERSION = "0.1.0";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private TokenLogin() {
    }
}
