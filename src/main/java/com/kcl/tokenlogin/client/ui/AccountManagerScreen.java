package com.kcl.tokenlogin.client.ui;

import com.kcl.tokenlogin.client.auth.Account;
import com.kcl.tokenlogin.client.auth.AccountService;
import com.kcl.tokenlogin.client.auth.AccountStore;
import com.kcl.tokenlogin.client.auth.UserManager;
import com.kcl.tokenlogin.client.avatar.AvatarCache;
import com.kcl.tokenlogin.client.config.TokenLoginConfig;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The account manager: a searchable list of imported accounts with avatar,
 * access token expiry, refresh token age, whether tokens refresh on their own,
 * and per-account login / refresh / delete actions.
 *
 * <p>Every colour here carries an explicit {@code 0xFF} alpha. The 1.21.11
 * {@code DrawContext} drops text whose colour has an alpha of zero, which is why
 * plain {@code 0xFFFFFF} labels used to be invisible.</p>
 */
public final class AccountManagerScreen extends Screen {
    public static final int COLOR_SUCCESS = 0xFF55FF55;
    public static final int COLOR_ERROR = 0xFFFF5555;
    public static final int COLOR_INFO = 0xFFFFFF55;

    private static final int ROW_HEIGHT = 62;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_MUTED = 0xFFA0A0A0;
    /** Width of one action button and the gap between them. */
    private static final int BUTTON_WIDTH = 58;
    private static final int BUTTON_GAP = 4;
    private static final int ACTION_STRIP = BUTTON_WIDTH * 3 + BUTTON_GAP * 2;

    private final Screen parent;
    /** Account to scroll into view after an import, or {@code null}. */
    private final String focusId;
    private final List<RowAction> rowActions = new ArrayList<>();
    private TextFieldWidget searchField;
    private ButtonWidget addButton;
    private ButtonWidget backButton;
    private ButtonWidget settingsButton;
    private double scroll;
    private Text status;
    private int statusColor = COLOR_INFO;
    private long statusTime;

    private int listX;
    private int listY;
    private int listW;
    private int listH;

    public AccountManagerScreen(Screen parent) {
        this(parent, null, COLOR_INFO, null);
    }

    public AccountManagerScreen(Screen parent, Text initialStatus, int initialColor) {
        this(parent, initialStatus, initialColor, null);
    }

    public AccountManagerScreen(Screen parent, Text initialStatus, int initialColor, String focusId) {
        super(Text.translatable("tokenlogin.title"));
        this.parent = parent;
        this.focusId = focusId;
        if (initialStatus != null) {
            this.status = initialStatus;
            this.statusColor = initialColor;
            this.statusTime = System.currentTimeMillis();
        }
    }

    @Override
    protected void init() {
        this.listX = 20;
        this.listY = 64;
        this.listW = Math.max(120, this.width - 40);
        this.listH = Math.max(40, this.height - 122);
        scrollToFocused();

        this.searchField = new TextFieldWidget(this.textRenderer, 20, 36,
            Math.max(60, this.width - 260), 20, Text.translatable("tokenlogin.search"));
        this.searchField.setMaxLength(64);
        this.searchField.setPlaceholder(Text.translatable("tokenlogin.search.placeholder"));
        this.searchField.setChangedListener(text -> this.scroll = 0d);
        this.addDrawableChild(this.searchField);

        this.settingsButton = ButtonWidget.builder(Text.translatable("tokenlogin.settings"),
                pressed -> this.client.setScreen(new SettingsScreen(this)))
            .dimensions(this.width - 120, 36, 100, 20)
            .build();
        this.addDrawableChild(this.settingsButton);

        this.addButton = ButtonWidget.builder(Text.translatable("tokenlogin.add"),
                pressed -> this.client.setScreen(new AddAccountScreen(this)))
            .dimensions(this.width / 2 - 55, this.height - 34, 110, 20)
            .build();
        this.addDrawableChild(this.addButton);

        this.backButton = ButtonWidget.builder(Text.translatable("tokenlogin.back"),
                pressed -> this.close())
            .dimensions(20, this.height - 34, 80, 20)
            .build();
        this.addDrawableChild(this.backButton);
    }

    /** Puts a freshly imported account on screen instead of leaving it below the fold. */
    private void scrollToFocused() {
        if (this.focusId == null) {
            return;
        }
        List<Account> accounts = AccountStore.get().snapshot();
        for (int i = 0; i < accounts.size(); i++) {
            if (this.focusId.equals(accounts.get(i).normalizedId())) {
                this.scroll = Math.max(0, i * ROW_HEIGHT - (this.listH - ROW_HEIGHT) / 2d);
                return;
            }
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 16, COLOR_TEXT);
        context.fill(this.listX - 1, this.listY - 1, this.listX + this.listW + 1,
            this.listY + this.listH + 1, 0x66000000);
        renderList(context, mouseX, mouseY);
        renderStatus(context);
        super.render(context, mouseX, mouseY, delta);
    }

    private void renderList(DrawContext context, int mouseX, int mouseY) {
        List<Account> accounts = AccountStore.get().snapshot();
        String query = this.searchField == null ? "" : this.searchField.getText().trim().toLowerCase(Locale.ROOT);
        List<Account> filtered = new ArrayList<>();
        for (Account account : accounts) {
            String id = account.normalizedId();
            if (query.isEmpty()
                || account.getName().toLowerCase(Locale.ROOT).contains(query)
                || (id != null && id.contains(query))) {
                filtered.add(account);
            }
        }

        int contentHeight = filtered.size() * ROW_HEIGHT;
        this.scroll = Math.max(0d, Math.min(this.scroll, Math.max(0, contentHeight - this.listH)));
        this.rowActions.clear();

        if (filtered.isEmpty()) {
            Text empty = accounts.isEmpty()
                ? Text.translatable("tokenlogin.empty")
                : Text.translatable("tokenlogin.search.empty");
            context.drawCenteredTextWithShadow(this.textRenderer, empty,
                this.listX + this.listW / 2, this.listY + this.listH / 2 - 4, COLOR_MUTED);
            return;
        }

        context.enableScissor(this.listX, this.listY, this.listX + this.listW, this.listY + this.listH);
        for (int i = 0; i < filtered.size(); i++) {
            int rowY = this.listY + i * ROW_HEIGHT - (int) Math.round(this.scroll);
            if (rowY + ROW_HEIGHT < this.listY || rowY > this.listY + this.listH) {
                continue;
            }
            renderRow(context, filtered.get(i), this.listX, rowY, this.listW, mouseX, mouseY);
        }
        context.disableScissor();

        if (contentHeight > this.listH) {
            int barHeight = Math.max(20, this.listH * this.listH / contentHeight);
            double maxScroll = contentHeight - this.listH;
            int barY = this.listY
                + (int) ((this.listH - barHeight) * (maxScroll <= 0d ? 0d : this.scroll / maxScroll));
            context.fill(this.listX + this.listW - 4, barY, this.listX + this.listW - 1,
                barY + barHeight, 0x88FFFFFF);
        }
    }

    private void renderRow(DrawContext context, Account account, int x, int y, int width,
                           int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + ROW_HEIGHT - 2;
        context.fill(x, y, x + width, y + ROW_HEIGHT - 2, hovered ? 0x66303A44 : 0x33202A30);
        context.fill(x, y + ROW_HEIGHT - 2, x + width, y + ROW_HEIGHT - 1, 0xAA555555);

        int avatarSize = 20;
        int avatarX = x + 6;
        int avatarY = y + 6;
        renderAvatar(context, account, avatarX, avatarY, avatarSize);

        // The action buttons live on the right; keep the text off them.
        int textRight = x + width - ACTION_STRIP - 14;
        int nameWidth = Math.max(40, textRight - (x + 32));

        MutableText name = Text.literal(this.textRenderer.trimToWidth(account.getName(), nameWidth));
        if (UserManager.isCurrent(account) && nameWidth >= 90) {
            name.append(Text.literal(" "));
            name.append(Text.translatable("tokenlogin.account.current").formatted(Formatting.GREEN));
        }
        context.drawTextWithShadow(this.textRenderer, name, x + 32, y + 4, COLOR_TEXT);

        Text accessLine;
        int accessColor;
        if (account.isRefreshInvalid()) {
            accessLine = Text.translatable("tokenlogin.account.at_invalid");
            accessColor = COLOR_ERROR;
        } else if (account.remainingAccessMillis() <= 0L) {
            accessLine = Text.translatable("tokenlogin.account.at_expired");
            accessColor = COLOR_ERROR;
        } else {
            accessLine = Text.translatable("tokenlogin.account.at_remaining",
                TimeText.duration(account.remainingAccessMillis()));
            accessColor = COLOR_SUCCESS;
        }
        context.drawTextWithShadow(this.textRenderer, accessLine, x + 32, y + 17, accessColor);

        Text refreshLine;
        int refreshColor;
        if (account.isRefreshInvalid()) {
            refreshLine = Text.translatable("tokenlogin.account.rt_invalid");
            refreshColor = COLOR_ERROR;
        } else if (account.getImportedAt() <= 0L) {
            refreshLine = Text.translatable("tokenlogin.account.rt_unknown");
            refreshColor = COLOR_MUTED;
        } else {
            long importedAgo = System.currentTimeMillis() - account.getImportedAt();
            refreshLine = Text.translatable("tokenlogin.account.rt_imported", TimeText.duration(importedAgo));
            refreshColor = COLOR_MUTED;
        }
        context.drawTextWithShadow(this.textRenderer, refreshLine, x + 32, y + 30, refreshColor);

        Text autoLine;
        int autoColor;
        if (account.isRefreshInvalid()) {
            autoLine = Text.translatable("tokenlogin.account.auto_off_invalid");
            autoColor = COLOR_ERROR;
        } else if (TokenLoginConfig.isAutoRefresh()) {
            autoLine = Text.translatable("tokenlogin.account.auto_on",
                TimeText.duration(AccountService.REFRESH_AHEAD_MS));
            autoColor = COLOR_INFO;
        } else {
            autoLine = Text.translatable("tokenlogin.account.auto_off");
            autoColor = COLOR_MUTED;
        }
        context.drawTextWithShadow(this.textRenderer, autoLine, x + 32, y + 43, autoColor);

        int buttonY = y + (ROW_HEIGHT - 2 - 20) / 2;
        int buttonX = x + width - ACTION_STRIP - 6;
        if (account.isRefreshing()) {
            drawActionButton(context, Text.translatable("tokenlogin.action.refreshing"),
                buttonX, buttonY, BUTTON_WIDTH, 20, mouseX, mouseY, 0xFF555555, 0xFF555555, null);
        } else {
            drawActionButton(context, Text.translatable("tokenlogin.action.login"),
                buttonX, buttonY, BUTTON_WIDTH, 20, mouseX, mouseY, 0xFF2E7D32, 0xFF3E9142,
                () -> login(account));
        }
        drawActionButton(context, Text.translatable("tokenlogin.action.refresh"),
            buttonX + BUTTON_WIDTH + BUTTON_GAP, buttonY, BUTTON_WIDTH, 20, mouseX, mouseY,
            0xFF37474F, 0xFF4A5F6A, () -> refresh(account));
        drawActionButton(context, Text.translatable("tokenlogin.action.delete"),
            buttonX + (BUTTON_WIDTH + BUTTON_GAP) * 2, buttonY, BUTTON_WIDTH, 20, mouseX, mouseY,
            0xFF7F2A2A, 0xFF9A3535, () -> delete(account));
    }

    /** Uses the vanilla skin drawer so the head looks exactly like the server list. */
    private static void renderAvatar(DrawContext context, Account account, int x, int y, int size) {
        Identifier skin = AvatarCache.get(account.normalizedId());
        if (skin != null) {
            PlayerSkinDrawer.draw(context, skin, x, y, size, true, false, 0xFFFFFFFF);
            return;
        }
        drawPlaceholderAvatar(context, x, y, size);
    }

    private void drawActionButton(DrawContext context, Text label, int x, int y, int width, int height,
                                  int mouseX, int mouseY, int baseColor, int hoverColor, Runnable action) {
        boolean hovered = action != null && mouseX >= x && mouseX < x + width
            && mouseY >= y && mouseY < y + height;
        context.fill(x, y, x + width, y + height, hovered ? hoverColor : baseColor);
        context.fill(x, y, x + width, y + 1, 0x55FFFFFF);
        context.fill(x, y + height - 1, x + width, y + height, 0x55000000);
        context.drawCenteredTextWithShadow(this.textRenderer, label, x + width / 2,
            y + (height - 8) / 2, action == null ? 0xFF999999 : COLOR_TEXT);
        // Rows are clipped by a scissor, so a button that is only half visible
        // must not be clickable outside the list either.
        if (action != null && y >= this.listY && y + height <= this.listY + this.listH) {
            this.rowActions.add(new RowAction(x, y, width, height, action));
        }
    }

    private static void drawPlaceholderAvatar(DrawContext context, int x, int y, int size) {
        context.fill(x, y, x + size, y + size, 0xFF4A3B32);
        context.fill(x + 2, y + 3, x + size - 2, y + size - 2, 0xFFC68E6B);
        context.fill(x + 4, y + 7, x + 8, y + 10, 0xFF2B2B2B);
        context.fill(x + size - 8, y + 7, x + size - 4, y + 10, 0xFF2B2B2B);
        context.fill(x + 6, y + size - 6, x + size - 6, y + size - 4, 0xFF8A5A3C);
    }

    private void renderStatus(DrawContext context) {
        if (this.status == null) {
            return;
        }
        if (System.currentTimeMillis() - this.statusTime > 10_000L) {
            this.status = null;
            return;
        }
        context.drawCenteredTextWithShadow(this.textRenderer, this.status, this.width / 2,
            this.height - 48, this.statusColor);
    }

    private void setStatus(Text message, int color) {
        this.status = message;
        this.statusColor = color;
        this.statusTime = System.currentTimeMillis();
    }

    private void login(Account account) {
        setStatus(Text.translatable("tokenlogin.status.logging_in", account.getName()), COLOR_INFO);
        AccountService.login(account, result -> setStatus(result.message(),
            result.ok() ? COLOR_SUCCESS : COLOR_ERROR));
    }

    private void refresh(Account account) {
        if (account.isRefreshing()) {
            return;
        }
        setStatus(Text.translatable("tokenlogin.status.refreshing", account.getName()), COLOR_INFO);
        AccountService.refresh(account, result -> {
            if (result.ok()) {
                setStatus(Text.translatable("tokenlogin.status.refresh_ok", account.getName(),
                    TimeText.duration(account.remainingAccessMillis())), COLOR_SUCCESS);
            } else if (result.failures() >= AccountService.MAX_REFRESH_FAILURES) {
                setStatus(Text.translatable("tokenlogin.status.refresh_invalid", account.getName()),
                    COLOR_ERROR);
            } else if (result.failure() != null) {
                Text reason = AccountService.failureText(result.failure());
                setStatus(Text.translatable("tokenlogin.status.refresh_failed", result.failures(), reason),
                    COLOR_ERROR);
            } else {
                setStatus(Text.translatable("tokenlogin.status.refreshing", account.getName()), COLOR_ERROR);
            }
        });
    }

    private void delete(Account account) {
        AccountStore.get().remove(account);
        setStatus(Text.translatable("tokenlogin.status.deleted", account.getName()), COLOR_INFO);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) {
            return true;
        }
        double mouseX = click.x();
        double mouseY = click.y();
        if (mouseX >= this.listX && mouseX < this.listX + this.listW
            && mouseY >= this.listY && mouseY < this.listY + this.listH) {
            for (int i = this.rowActions.size() - 1; i >= 0; i--) {
                RowAction action = this.rowActions.get(i);
                if (action.contains(mouseX, mouseY)) {
                    action.action().run();
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
            return true;
        }
        if (mouseX >= this.listX && mouseX < this.listX + this.listW
            && mouseY >= this.listY && mouseY < this.listY + this.listH) {
            this.scroll -= verticalAmount * 24d;
            return true;
        }
        return false;
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }

    private record RowAction(int x, int y, int width, int height, Runnable action) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }
}
