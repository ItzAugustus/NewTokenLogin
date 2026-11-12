package com.kcl.tokenlogin.client;

import com.kcl.tokenlogin.TokenLogin;
import com.kcl.tokenlogin.client.ui.AccountManagerScreen;
import com.kcl.tokenlogin.client.ui.AddAccountScreen;
import com.kcl.tokenlogin.client.ui.SettingsScreen;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

/**
 * Optional hotkey that opens the account manager.
 *
 * <p>It exists because the multiplayer button can be moved to "无" (hidden) and
 * Mod Menu is only a recommended dependency: without either one there would be
 * no way into the mod at all. The key is unbound by default so it never fights
 * with another mod; the player can bind it under Controls → Key Binds →
 * Multiplayer.</p>
 */
public final class TokenLoginKeys {
    private static final String OPEN_MANAGER = "key.tokenlogin.open_manager";

    private static KeyBinding openManager;

    private TokenLoginKeys() {
    }

    public static void register() {
        try {
            openManager = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                OPEN_MANAGER, InputUtil.UNKNOWN_KEY.getCode(), KeyBinding.Category.MULTIPLAYER));
        } catch (Throwable throwable) {
            // A broken key binding must never stop the mod from loading.
            TokenLogin.LOGGER.warn("Could not register the TokenLogin hotkey", throwable);
        }
    }

    /** Called from the client tick; consumes queued presses. */
    public static void handleInput() {
        if (openManager == null) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        while (openManager.wasPressed()) {
            if (isOurScreen(client.currentScreen)) {
                continue;
            }
            client.setScreen(new AccountManagerScreen(client.currentScreen));
        }
    }

    private static boolean isOurScreen(Screen screen) {
        return screen instanceof AccountManagerScreen
            || screen instanceof SettingsScreen
            || screen instanceof AddAccountScreen;
    }
}
