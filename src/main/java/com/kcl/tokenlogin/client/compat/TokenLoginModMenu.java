package com.kcl.tokenlogin.client.compat;

import com.kcl.tokenlogin.client.ui.SettingsScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Mod Menu integration. Only loaded when Mod Menu is installed, so TokenLogin
 * itself does not need it at runtime.
 */
public final class TokenLoginModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new SettingsScreen(parent);
    }
}
