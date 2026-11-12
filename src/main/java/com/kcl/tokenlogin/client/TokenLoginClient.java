package com.kcl.tokenlogin.client;

import com.kcl.tokenlogin.client.auth.AccountService;
import com.kcl.tokenlogin.client.auth.AccountStore;
import com.kcl.tokenlogin.client.config.TokenLoginConfig;
import com.kcl.tokenlogin.client.ui.TokenLoginUi;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public final class TokenLoginClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        TokenLoginConfig.load();
        AccountStore.get().load();
        TokenLoginUi.register();
        TokenLoginKeys.register();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            AccountService.tick();
            TokenLoginKeys.handleInput();
        });
    }
}
