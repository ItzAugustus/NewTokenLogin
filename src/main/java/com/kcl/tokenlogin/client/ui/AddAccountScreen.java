package com.kcl.tokenlogin.client.ui;

import com.kcl.tokenlogin.client.auth.AccountService;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/**
 * Paste a refresh token (or a whole launcher account blob) and import it.
 */
public final class AddAccountScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget tokenField;
    private ButtonWidget pasteButton;
    private ButtonWidget importButton;
    private ButtonWidget backButton;
    private Text status;
    private int statusColor = AccountManagerScreen.COLOR_INFO;
    private long statusTime;
    /** Errors stay on screen until the next action instead of fading out. */
    private boolean statusPersistent;
    private boolean busy;

    public AddAccountScreen(Screen parent) {
        super(Text.translatable("tokenlogin.import.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int fieldWidth = Math.min(this.width - 80, 420);
        int fieldX = (this.width - fieldWidth) / 2;
        int fieldY = this.height / 2 - 6;

        this.tokenField = new TextFieldWidget(this.textRenderer, fieldX, fieldY, fieldWidth, 20,
            Text.translatable("tokenlogin.import.field"));
        this.tokenField.setMaxLength(65536);
        this.tokenField.setPlaceholder(Text.translatable("tokenlogin.import.placeholder"));
        this.addDrawableChild(this.tokenField);

        int buttonY = fieldY + 30;
        int buttonWidth = Math.max(56, Math.min(110, (this.width - 96) / 3));
        int totalWidth = buttonWidth * 3 + 16;
        int buttonX = (this.width - totalWidth) / 2;

        this.pasteButton = ButtonWidget.builder(Text.translatable("tokenlogin.import.paste"),
                pressed -> pasteFromClipboard())
            .dimensions(buttonX, buttonY, buttonWidth, 20)
            .build();
        this.addDrawableChild(this.pasteButton);

        this.importButton = ButtonWidget.builder(Text.translatable("tokenlogin.import.button"),
                pressed -> startImport())
            .dimensions(buttonX + buttonWidth + 8, buttonY, buttonWidth, 20)
            .build();
        this.addDrawableChild(this.importButton);

        this.backButton = ButtonWidget.builder(Text.translatable("tokenlogin.import.back"),
                pressed -> this.close())
            .dimensions(buttonX + (buttonWidth + 8) * 2, buttonY, buttonWidth, 20)
            .build();
        this.addDrawableChild(this.backButton);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFFFF);
        context.drawWrappedTextWithShadow(this.textRenderer,
            Text.translatable("tokenlogin.import.hint"), 40, 44, this.width - 80, 0xFFA0A0A0);
        renderStatus(context);
        super.render(context, mouseX, mouseY, delta);
    }

    private void renderStatus(DrawContext context) {
        if (this.status == null) {
            return;
        }
        if (!this.statusPersistent && System.currentTimeMillis() - this.statusTime > 10_000L) {
            this.status = null;
            return;
        }
        context.drawCenteredTextWithShadow(this.textRenderer, this.status, this.width / 2,
            this.height / 2 + 56, this.statusColor);
    }

    private void setStatus(Text message, int color) {
        setStatus(message, color, false);
    }

    private void setStatus(Text message, int color, boolean persistent) {
        this.status = message;
        this.statusColor = color;
        this.statusTime = System.currentTimeMillis();
        this.statusPersistent = persistent;
    }

    private void setBusy(boolean busy) {
        this.busy = busy;
        if (this.importButton != null) {
            this.importButton.active = !busy;
        }
        if (this.pasteButton != null) {
            this.pasteButton.active = !busy;
        }
        if (this.backButton != null) {
            this.backButton.active = !busy;
        }
        if (this.tokenField != null) {
            this.tokenField.setEditable(!busy);
        }
    }

    private void pasteFromClipboard() {
        if (this.busy) {
            return;
        }
        String clipboard;
        try {
            clipboard = this.client.keyboard.getClipboard();
        } catch (Exception e) {
            setStatus(Text.translatable("tokenlogin.status.clipboard_error"),
                AccountManagerScreen.COLOR_ERROR, true);
            return;
        }
        if (clipboard == null || clipboard.isBlank()) {
            setStatus(Text.translatable("tokenlogin.status.clipboard_empty"),
                AccountManagerScreen.COLOR_ERROR, true);
            return;
        }
        this.tokenField.setText(clipboard);
        setStatus(Text.translatable("tokenlogin.status.pasted", clipboard.length()),
            AccountManagerScreen.COLOR_INFO);
    }

    private void startImport() {
        if (this.busy) {
            return;
        }
        String raw = this.tokenField.getText();
        if (raw == null || raw.isBlank()) {
            setStatus(Text.translatable("tokenlogin.error.no_token"),
                AccountManagerScreen.COLOR_ERROR, true);
            return;
        }
        setBusy(true);
        setStatus(Text.translatable("tokenlogin.status.busy"), AccountManagerScreen.COLOR_INFO);
        AccountService.importToken(raw, result -> {
            setBusy(false);
            if (result.ok() && result.account() != null) {
                String name = result.account().getName();
                if (this.client.currentScreen == this) {
                    this.client.setScreen(new AccountManagerScreen(this.parent,
                        Text.translatable("tokenlogin.status.import_ok", name),
                        AccountManagerScreen.COLOR_SUCCESS, result.account().normalizedId()));
                }
            } else {
                // Keep the reason visible (and the pasted text in the field) so a
                // failed import cannot silently look like "nothing happened".
                setStatus(AccountService.failureText(result.failure()),
                    AccountManagerScreen.COLOR_ERROR, true);
            }
        });
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }
}
