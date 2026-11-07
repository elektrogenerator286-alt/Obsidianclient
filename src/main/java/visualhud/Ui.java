package visualhud;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.item.ItemStack;

/** Помощники отрисовки: мягкие скруглённые панели, круги, белая подсветка, мелкий текст. */
public final class Ui {
    public static final int PANEL = 0x2E2E2E;     // серый
    public static final int TEXT = 0xE6E6E6;
    public static final int WHITE = 0xFFFFFF;
    public static final int TEXT_DIM = 0xA8A8A8;
    public static final float TEXT_SCALE = 0.8f;  // обычный мелкий текст
    public static final float TINY_SCALE = 0.62f; // самый мелкий (эффекты, кулдауны, броня, меню)

    /** Общий множитель прозрачности (для плавного появления меню). 1 = без изменений. */
    private static float alphaMul = 1f;

    private Ui() {}

    public static void setAlpha(float a) {
        alphaMul = Math.max(0f, Math.min(1f, a));
    }

    public static float alpha() {
        return alphaMul;
    }

    public static int argb(int rgb, int alpha) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * alphaMul)));
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    /** Одна скруглённая плашка (строка за строкой). */
    public static void roundRect(DrawContext ctx, int x, int y, int w, int h, int r, int color) {
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        for (int i = 0; i < h; i++) {
            int inset = 0;
            if (i < r) {
                double dy = r - i - 0.5;
                inset = (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
            } else if (i >= h - r) {
                double dy = i - (h - r) + 0.5;
                inset = (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
            }
            ctx.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
        }
    }

    public static void softPanel(DrawContext ctx, int x, int y, int w, int h, int r) {
        softPanel(ctx, x, y, w, h, r, 0f);
    }

    /**
     * Плашка с белой подсветкой снаружи и прозрачными краями (центр плотнее).
     * flash (0..1) добавляет широкий мягкий ореол, им подсвечивается появление меню.
     */
    public static void softPanel(DrawContext ctx, int x, int y, int w, int h, int r, float flash) {
        if (flash > 0.01f) {
            for (int k = 8; k >= 4; k--) {
                int a = Math.round(11f * flash * (9 - k) / 5f);
                if (a > 0) roundRect(ctx, x - k, y - k, w + 2 * k, h + 2 * k, r + k, argb(0xFFFFFF, a));
            }
        }
        for (int k = 3; k >= 1; k--) {
            roundRect(ctx, x - k, y - k, w + 2 * k, h + 2 * k, r + k, argb(0xFFFFFF, 14));
        }
        for (int k = 0; k < 3; k++) {
            roundRect(ctx, x + k, y + k, w - 2 * k, h - 2 * k, Math.max(1, r - k), argb(PANEL, 62));
        }
    }

    public static void circle(DrawContext ctx, int cx, int cy, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            int dx = (int) Math.floor(Math.sqrt(radius * radius + radius - dy * dy));
            ctx.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }

    /** Круг (для второй руки) с белой подсветкой и прозрачными краями. */
    public static void softCircle(DrawContext ctx, int cx, int cy, int radius) {
        for (int k = 3; k >= 1; k--) {
            circle(ctx, cx, cy, radius + k, argb(0xFFFFFF, 14));
        }
        for (int k = 0; k < 4; k++) {
            circle(ctx, cx, cy, radius - k, argb(PANEL, 52));
        }
    }

    public static void text(DrawContext ctx, TextRenderer tr, String s, int x, int y, int color) {
        text(ctx, tr, s, x, y, color, TEXT_SCALE);
    }

    public static void text(DrawContext ctx, TextRenderer tr, String s, int x, int y, int color, float scale) {
        int a = Math.round(255 * alphaMul);
        if (a < 8) return; // при очень малой прозрачности Minecraft рисует текст непрозрачным
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(x, y);
        ctx.getMatrices().scale(scale, scale);
        ctx.drawText(tr, s, 0, 0, (a << 24) | (color & 0xFFFFFF), false);
        ctx.getMatrices().popMatrix();
    }

    public static int textWidth(TextRenderer tr, String s) {
        return textWidth(tr, s, TEXT_SCALE);
    }

    public static int textWidth(TextRenderer tr, String s, float scale) {
        return (int) Math.ceil(tr.getWidth(s) * scale);
    }

    public static int textHeight(TextRenderer tr) {
        return textHeight(tr, TEXT_SCALE);
    }

    public static int textHeight(TextRenderer tr, float scale) {
        return (int) Math.ceil(tr.fontHeight * scale);
    }

    /** Предмет, уменьшенный в scale раз (1.0 = 16x16). */
    public static void item(DrawContext ctx, ItemStack stack, int x, int y, float scale) {
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(x, y);
        ctx.getMatrices().scale(scale, scale);
        ctx.drawItem(stack, 0, 0);
        ctx.getMatrices().popMatrix();
    }
}
