package com.kcl.tokenlogin.client.ui;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

/** Human readable duration strings such as "2天 3小时". */
public final class TimeText {
    private TimeText() {
    }

    public static Text duration(long millis) {
        long totalSeconds = Math.max(0L, millis) / 1000L;
        long days = totalSeconds / 86_400L;
        long hours = (totalSeconds % 86_400L) / 3_600L;
        long minutes = (totalSeconds % 3_600L) / 60L;
        long seconds = totalSeconds % 60L;

        MutableText result = Text.empty();
        boolean any = false;
        if (days > 0) {
            result.append(Text.translatable("tokenlogin.time.days", days));
            any = true;
        }
        if (hours > 0) {
            if (any) {
                result.append(Text.literal(" "));
            }
            result.append(Text.translatable("tokenlogin.time.hours", hours));
            any = true;
        }
        if (minutes > 0 && days == 0) {
            if (any) {
                result.append(Text.literal(" "));
            }
            result.append(Text.translatable("tokenlogin.time.minutes", minutes));
            any = true;
        }
        if (!any) {
            result.append(Text.translatable("tokenlogin.time.seconds", seconds));
        }
        return result;
    }
}
