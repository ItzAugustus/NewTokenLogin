package com.kcl.tokenlogin.client.ui;

import com.kcl.tokenlogin.TokenLogin;
import com.kcl.tokenlogin.client.config.TokenLoginConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * General settings; also opened from Mod Menu when it is installed.
 *
 * <p>Colours are written with an explicit {@code 0xFF} alpha because the 1.21.11
 * {@code DrawContext} silently skips any text whose colour is fully transparent.</p>
 */
public final class SettingsScreen extends Screen {
    private static final int COLOR_LABEL = 0xFFFFFFFF;
    private static final int COLOR_HINT = 0xFFA0A0A0;
    private static final int COLOR_SECTION = 0xFFFFFF55;
    private static final int LEFT = 40;
    private static final int ROW_WIDTH = 180;
    /** Vertical size of the whole block, used to centre it. */
    private static final int BLOCK_HEIGHT = 176;

    private final Screen parent;
    private ButtonWidget autoRefreshButton;

    public SettingsScreen(Screen parent) {
        super(Text.translatable("tokenlogin.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int top = blockTop();

        this.addDrawableChild(ButtonWidget.builder(positionMessage(), pressed -> {
                TokenLoginConfig.cycleButtonPosition();
                pressed.setMessage(positionMessage());
            })
            .dimensions(LEFT, top + 28, ROW_WIDTH, 20)
            .build());

        this.autoRefreshButton = ButtonWidget.builder(autoRefreshMessage(), pressed -> {
                TokenLoginConfig.toggleAutoRefresh();
                pressed.setMessage(autoRefreshMessage());
            })
            .dimensions(LEFT, top + 90, ROW_WIDTH, 20)
            .build();
        this.addDrawableChild(this.autoRefreshButton);

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("tokenlogin.settings.open_accounts"),
                pressed -> this.client.setScreen(new AccountManagerScreen(this)))
            .dimensions(LEFT, top + 154, ROW_WIDTH, 20)
            .build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("tokenlogin.settings.done"),
                pressed -> this.close())
            .dimensions(this.width / 2 - 60, this.height - 36, 120, 20)
            .build());
    }

    private int blockTop() {
        int preferred = (this.height - BLOCK_HEIGHT) / 2 - 10;
        int max = this.height - BLOCK_HEIGHT - 56;
        return Math.max(44, Math.min(preferred, max));
    }

    private static Text positionMessage() {
        return Text.translatable("tokenlogin.settings.position_value",
            Text.translatable(TokenLoginConfig.getButtonPosition().translationKey()));
    }

    private static Text autoRefreshMessage() {
        return Text.translatable("tokenlogin.settings.auto_refresh_value",
            Text.translatable(TokenLoginConfig.isAutoRefresh()
                ? "tokenlogin.common.on"
                : "tokenlogin.common.off"));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, COLOR_LABEL);

        int top = blockTop();
        int noteWidth = this.width - LEFT * 2;

        context.drawTextWithShadow(this.textRenderer, Text.translatable("tokenlogin.settings.general"),
            LEFT, top, COLOR_SECTION);
        context.fill(LEFT, top + 12, this.width - LEFT, top + 13, 0x80FFFFFF);

        context.drawTextWithShadow(this.textRenderer, Text.translatable("tokenlogin.settings.position"),
            LEFT, top + 16, COLOR_LABEL);
        context.drawWrappedTextWithShadow(this.textRenderer,
            Text.translatable("tokenlogin.settings.position.note"), LEFT, top + 52, noteWidth, COLOR_HINT);

        context.drawTextWithShadow(this.textRenderer, Text.translatable("tokenlogin.settings.auto_refresh"),
            LEFT, top + 78, COLOR_LABEL);
        context.drawWrappedTextWithShadow(this.textRenderer,
            Text.translatable("tokenlogin.settings.auto_refresh.note"), LEFT, top + 114, noteWidth, COLOR_HINT);

        context.drawTextWithShadow(this.textRenderer, Text.translatable("tokenlogin.settings.accounts"),
            LEFT, top + 142, COLOR_LABEL);

        if (this.height >= 320) {
            context.drawWrappedTextWithShadow(this.textRenderer,
                Text.translatable("tokenlogin.settings.hotkey_hint"), LEFT, this.height - 62,
                noteWidth, COLOR_HINT);
        }
        context.drawTextWithShadow(this.textRenderer,
            Text.literal(TokenLogin.MOD_NAME + " " + version() + " · " + TokenLogin.AUTHOR),
            20, this.height - 18, 0xFF808080);
        super.render(context, mouseX, mouseY, delta);
    }

    /** The version the loader actually loaded, so the footer matches the jar. */
    private static String version() {
        return FabricLoader.getInstance().getModContainer(TokenLogin.MOD_ID)
            .map(container -> container.getMetadata().getVersion().getFriendlyString())
            .orElse(TokenLogin.VERSION);
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }
}
