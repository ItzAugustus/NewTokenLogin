package com.kcl.tokenlogin.client.ui;

import com.kcl.tokenlogin.client.config.TokenLoginConfig;
import com.kcl.tokenlogin.client.config.TokenLoginConfig.ButtonPosition;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Adds the "Token login" button to the multiplayer screen. The corner is
 * configurable, and can be disabled entirely.
 *
 * <p>The corner is shared with other mods: ViaFabricPlus (and a few others) drop
 * their own button into the same spot and their default is the top right corner,
 * exactly where we used to put ours. Back then our button was added last, so it
 * was drawn on top of theirs and also swallowed their clicks - the ViaFabricPlus
 * button simply "disappeared". We therefore look at the widgets that are already
 * on the screen and slide our button along the edge until it does not overlap
 * any of them, and we redo that every tick so it keeps working after a window
 * resize (the other mod repositions its button on resize too).
 *
 * <p>Fabric fires {@code ScreenEvents.AFTER_INIT} every time
 * {@code Screen.init(int, int)} runs, and that method runs again whenever a
 * screen is shown a second time: returning from the account manager re-uses the
 * very same {@link MultiplayerScreen} instance. A multiplayer screen only
 * repositions its widgets on that second pass (it overrides
 * {@code refreshWidgetPositions()}), it does not clear its children, so adding a
 * button on every AFTER_INIT left one extra copy behind every time the account
 * manager was closed - up to three, because the placement code slides the
 * duplicates along the edge through its three columns. We now keep one button per
 * screen instance and only build a new one when the old one is really gone.
 */
public final class TokenLoginUi {
    private static final int BUTTON_WIDTH = 100;
    private static final int BUTTON_HEIGHT = 20;
    private static final int MARGIN = 6;
    private static final int GAP = 4;
    /** How many buttons we try to fit side by side along the edge. */
    private static final int COLUMNS = 3;
    /**
     * The vanilla multiplayer screen keeps a 33px tall header and a 60px tall
     * footer. At the top there is only room for a single row (a second row would
     * cover the server list), at the bottom one extra row still fits.
     */
    private static final int TOP_ROWS = 1;
    private static final int BOTTOM_ROWS = 2;

    /** Our button on each multiplayer screen; weak so closed screens are collected. */
    private static final Map<Screen, ButtonWidget> BUTTONS = new WeakHashMap<>();
    /** Screens that already carry the per-tick re-placement hook. */
    private static final Set<Screen> TICKING = Collections.newSetFromMap(new WeakHashMap<>());

    private TokenLoginUi() {
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof MultiplayerScreen)) {
                return;
            }
            ButtonPosition configured = TokenLoginConfig.getButtonPosition();
            if (configured == ButtonPosition.NONE) {
                return;
            }
            ButtonWidget button = buttonFor(client, screen);
            if (button == null) {
                return;
            }
            if (TICKING.add(screen)) {
                // Other mods move their buttons when the window is resized, so keep
                // re-evaluating where our button can sit without covering anything.
                ScreenEvents.afterTick(screen).register(ticked -> {
                    ButtonWidget current = BUTTONS.get(screen);
                    if (current != null) {
                        reposition(screen, current);
                    }
                });
            }
            place(screen, button);
        });
    }

    /**
     * The button that is already on this screen, or a freshly added one. Returns
     * {@code null} only if the screen refuses new children, which must never kill
     * the screen itself.
     */
    private static ButtonWidget buttonFor(MinecraftClient client, Screen screen) {
        ButtonWidget existing = BUTTONS.get(screen);
        if (existing != null && isAttached(screen, existing)) {
            return existing;
        }
        ButtonWidget button = ButtonWidget.builder(
                Text.translatable("tokenlogin.button"),
                // The handler reads the current screen instead of capturing the
                // multiplayer screen: BUTTONS is weakly keyed by the screen, and a
                // strong back-reference from the button would keep every closed
                // multiplayer screen alive for the rest of the session.
                pressed -> client.setScreen(new AccountManagerScreen(client.currentScreen)))
            .dimensions(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT)
            .build();
        if (!addButton(screen, button)) {
            return null;
        }
        BUTTONS.put(screen, button);
        return button;
    }

    /** Whether {@code button} is still one of the screen's children. */
    private static boolean isAttached(Screen screen, ButtonWidget button) {
        try {
            for (ClickableWidget widget : Screens.getButtons(screen)) {
                if (widget == button) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
            // A foreign screen must never break ours; treat it as detached.
        }
        return false;
    }

    private static void reposition(Screen screen, ButtonWidget button) {
        try {
            place(screen, button);
        } catch (Throwable ignored) {
            // Never let a foreign widget break the screen.
        }
    }

    /** Moves {@code button} into the configured corner, avoiding other widgets. */
    private static void place(Screen screen, ButtonWidget button) {
        ButtonPosition position = TokenLoginConfig.getButtonPosition();
        if (position == ButtonPosition.NONE) {
            button.visible = false;
            return;
        }
        button.visible = true;

        boolean right = position == ButtonPosition.TOP_RIGHT || position == ButtonPosition.BOTTOM_RIGHT;
        boolean bottom = position == ButtonPosition.BOTTOM_LEFT || position == ButtonPosition.BOTTOM_RIGHT;

        int baseX = right ? screen.width - BUTTON_WIDTH - MARGIN : MARGIN;
        int baseY = bottom ? screen.height - BUTTON_HEIGHT - MARGIN : MARGIN;
        // Slide inwards along the edge, then away from it.
        int stepX = right ? -(BUTTON_WIDTH + GAP) : BUTTON_WIDTH + GAP;
        int stepY = bottom ? -(BUTTON_HEIGHT + GAP) : BUTTON_HEIGHT + GAP;
        int rows = bottom ? BOTTOM_ROWS : TOP_ROWS;

        List<ClickableWidget> occupied = otherWidgets(screen, button);

        int bestX = baseX;
        int bestY = baseY;
        search:
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                int x = baseX + column * stepX;
                int y = baseY + row * stepY;
                if (x < MARGIN || y < MARGIN
                    || x + BUTTON_WIDTH > screen.width - MARGIN
                    || y + BUTTON_HEIGHT > screen.height - MARGIN) {
                    continue;
                }
                if (!overlaps(x, y, occupied)) {
                    bestX = x;
                    bestY = y;
                    break search;
                }
            }
        }

        if (button.getX() != bestX || button.getY() != bestY) {
            button.setX(bestX);
            button.setY(bestY);
        }
    }

    private static boolean overlaps(int x, int y, List<ClickableWidget> occupied) {
        for (ClickableWidget widget : occupied) {
            if (x < widget.getX() + widget.getWidth() && widget.getX() < x + BUTTON_WIDTH
                && y < widget.getY() + widget.getHeight() && widget.getY() < y + BUTTON_HEIGHT) {
                return true;
            }
        }
        return false;
    }

    /** Every visible widget on the screen except our own button. */
    private static List<ClickableWidget> otherWidgets(Screen screen, ButtonWidget own) {
        List<ClickableWidget> widgets = new ArrayList<>();
        for (Object child : (List<?>) screen.children()) {
            if (child instanceof ClickableWidget widget && widget != own && widget.visible) {
                widgets.add(widget);
            }
        }
        return widgets;
    }

    private static boolean addButton(Screen screen, ButtonWidget button) {
        try {
            Screens.getButtons(screen).add(button);
            return true;
        } catch (Throwable throwable) {
            return false;
        }
    }
}
