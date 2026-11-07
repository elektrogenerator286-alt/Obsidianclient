package visualhud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;

/**
 * Маленькое меню. Открывается по Right Shift, закрывается Right Shift или Esc.
 *
 * Управление:
 *  - клик по пункту: включить/выключить
 *  - правый клик (или +/-): раскрыть настройки пункта
 *  - переключатель: клик; ползунок: тянуть мышью; выбор: клик вперёд, правый клик назад
 *
 * Ввод читается напрямую через GLFW, поэтому код не зависит от сигнатур методов Screen,
 * которые меняются между версиями Minecraft.
 */
public class MenuScreen extends Screen {
    private static final int W = 104;
    private static final int PAD = 6;
    private static final int TITLE_H = 14;
    private static final int ROW = 12;
    private static final int SROW = 11;
    private static final int TRACK_W = 30;
    private static final int VALUE_W = 18;
    private static final float T = Ui.TINY_SCALE;

    private final Emitter emitter = new Emitter();
    private float t = 0f;                // 0..1, прогресс появления
    private boolean closing = false;
    private long last = System.nanoTime();
    private boolean prevL, prevR, prevKey, prevEsc;
    private float animH = -1f;
    private Setting.Slider dragging;
    private int dragTx;

    public MenuScreen() {
        super(Text.literal("Obsidian"));
        long h = MinecraftClient.getInstance().getWindow().getHandle();
        prevL = mouse(h, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        prevR = mouse(h, GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        prevKey = key(h, VisualHudClient.MENU_KEY);
        prevEsc = key(h, GLFW.GLFW_KEY_ESCAPE);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false; // Esc обрабатываем сами, чтобы сыграла анимация закрытия
    }

    private static boolean key(long handle, int k) {
        return GLFW.glfwGetKey(handle, k) == GLFW.GLFW_PRESS;
    }

    private static boolean mouse(long handle, int b) {
        return GLFW.glfwGetMouseButton(handle, b) == GLFW.GLFW_PRESS;
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static int targetHeight() {
        List<Feature> list = Features.list();
        int rows = list.isEmpty() ? ROW : 0;
        for (Feature f : list) {
            rows += ROW;
            if (f.expanded && !f.settings.isEmpty()) rows += f.settings.size() * SROW + 2;
        }
        return TITLE_H + 3 + rows + PAD;
    }

    private static String fmt(Setting.Slider s) {
        if (s.step >= 1) return String.valueOf(Math.round(s.get()));
        return String.format(Locale.ROOT, "%.1f", s.get());
    }

    private static void pill(DrawContext ctx, int x, int y, boolean on) {
        Ui.roundRect(ctx, x, y, 9, 5, 2, Ui.argb(Ui.WHITE, on ? 200 : 50));
        int kx = on ? x + 5 : x + 1;
        ctx.fill(kx, y + 1, kx + 3, y + 4, Ui.argb(on ? Ui.PANEL : Ui.WHITE, 230));
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;
        long handle = mc.getWindow().getHandle();

        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - last) / 1_000_000_000f);
        last = now;

        boolean lDown = mouse(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        boolean rDown = mouse(handle, GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        boolean keyDown = key(handle, VisualHudClient.MENU_KEY);
        boolean escDown = key(handle, GLFW.GLFW_KEY_ESCAPE);
        boolean lClick = lDown && !prevL;
        boolean rClick = rDown && !prevR;
        boolean closeKey = (keyDown && !prevKey) || (escDown && !prevEsc);
        prevL = lDown;
        prevR = rDown;
        prevKey = keyDown;
        prevEsc = escDown;

        if (closeKey && t > 0.3f) closing = true;
        t += closing ? -dt / 0.18f : dt / 0.30f;
        t = Math.max(0f, Math.min(1f, t));
        if (closing && t <= 0f) {
            Features.save();
            mc.setScreen(null);
            return;
        }
        boolean live = !closing && t >= 0.85f;

        float e = 1f - (float) Math.pow(1f - t, 3);   // плавное замедление к концу
        int th = Ui.textHeight(tr, T);

        int targetH = targetHeight();
        if (animH < 0f) animH = targetH;
        animH += (targetH - animH) * Math.min(1f, dt * 16f);
        int h = Math.round(animH);
        int sw = ctx.getScaledWindowWidth();
        int sh = ctx.getScaledWindowHeight();
        int x = (sw - W) / 2;
        int y = (sh - h) / 2;

        if (live && lClick && !inside(mouseX, mouseY, x, y, W, h)) closing = true;

        float flash = (float) Math.sin(Math.PI * Math.min(1f, t)) * (closing ? 0f : 1f);
        float scale = 0.94f + 0.06f * e;   // лёгкое «вырастание» на месте, без вылета из угла
        float cx = x + W / 2f, cy = y + h / 2f;

        Ui.setAlpha(e);
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(cx, cy);
        ctx.getMatrices().scale(scale, scale);
        ctx.getMatrices().translate(-cx, -cy);
        try {
            Ui.softPanel(ctx, x, y, W, h, 6, flash);
            emitter.draw(ctx, x, y, W, h);

            ctx.enableScissor(x, y, x + W, y + h);

            // заголовок и разделитель
            Ui.text(ctx, tr, "Obsidian", x + PAD, y + (TITLE_H - th) / 2 - 1, Ui.WHITE, T);
            ctx.fill(x + PAD, y + TITLE_H - 2, x + W - PAD, y + TITLE_H - 1, Ui.argb(Ui.WHITE, 40));

            int cyRow = y + TITLE_H + 3;
            int rx = x + W - PAD;
            int index = 0;

            List<Feature> list = Features.list();
            if (list.isEmpty()) {
                Ui.text(ctx, tr, "Пока пусто", x + PAD, cyRow + (ROW - th) / 2, Ui.TEXT_DIM, T);
            }

            for (Feature f : list) {
                float local = Math.max(0f, Math.min(1f, (t - index * 0.05f) / 0.6f));
                Ui.setAlpha(Math.min(e, local));
                index++;

                boolean hov = live && inside(mouseX, mouseY, x + 3, cyRow, W - 6, ROW);
                if (hov) Ui.roundRect(ctx, x + 3, cyRow, W - 6, ROW, 3, Ui.argb(Ui.WHITE, 22));

                pill(ctx, x + PAD, cyRow + (ROW - 5) / 2, f.enabled);
                Ui.text(ctx, tr, f.name, x + PAD + 13, cyRow + (ROW - th) / 2,
                        f.enabled ? Ui.WHITE : 0xB5B5B5, T);

                boolean hasSettings = !f.settings.isEmpty();
                if (hasSettings) {
                    String mark = f.expanded ? "-" : "+";
                    Ui.text(ctx, tr, mark, rx - Ui.textWidth(tr, mark, T), cyRow + (ROW - th) / 2, Ui.TEXT_DIM, T);
                }
                if (hov) {
                    boolean onMark = hasSettings && mouseX >= rx - 10;
                    if (lClick) {
                        if (onMark) f.expanded = !f.expanded;
                        else f.enabled = !f.enabled;
                    }
                    if (rClick && hasSettings) f.expanded = !f.expanded;
                }
                cyRow += ROW;

                if (f.expanded && hasSettings) {
                    int sx = x + PAD + 6;
                    for (Setting s : f.settings) {
                        boolean sh2 = live && inside(mouseX, mouseY, x + 3, cyRow, W - 6, SROW);
                        if (sh2) Ui.roundRect(ctx, x + 3, cyRow, W - 6, SROW, 3, Ui.argb(Ui.WHITE, 16));
                        int ty = cyRow + (SROW - th) / 2;
                        Ui.text(ctx, tr, s.name, sx, ty, Ui.TEXT, T);

                        if (s instanceof Setting.Bool b) {
                            pill(ctx, rx - 9, cyRow + (SROW - 5) / 2, b.value);
                            if (sh2 && lClick) b.value = !b.value;
                        } else if (s instanceof Setting.Slider sl) {
                            int tx = rx - VALUE_W - 4 - TRACK_W;
                            int ty2 = cyRow + SROW / 2;
                            Ui.roundRect(ctx, tx, ty2 - 1, TRACK_W, 2, 1, Ui.argb(Ui.WHITE, 50));
                            int fill = Math.round(TRACK_W * (float) sl.fraction());
                            if (fill > 0) Ui.roundRect(ctx, tx, ty2 - 1, fill, 2, 1, Ui.argb(Ui.WHITE, 200));
                            ctx.fill(tx + fill - 1, ty2 - 2, tx + fill + 1, ty2 + 2, Ui.argb(Ui.WHITE, 255));
                            String v = fmt(sl);
                            Ui.text(ctx, tr, v, rx - Ui.textWidth(tr, v, T), ty, Ui.WHITE, T);
                            if (live && lClick && inside(mouseX, mouseY, tx - 2, cyRow, TRACK_W + 4, SROW)) {
                                dragging = sl;
                                dragTx = tx;
                            }
                        } else if (s instanceof Setting.Choice c) {
                            String v = "< " + c.get() + " >";
                            Ui.text(ctx, tr, v, rx - Ui.textWidth(tr, v, T), ty, Ui.WHITE, T);
                            if (sh2 && lClick) c.next();
                            if (sh2 && rClick) c.prev();
                        }
                        cyRow += SROW;
                    }
                    cyRow += 2;
                }
            }

            if (dragging != null) {
                if (lDown) dragging.setFraction((mouseX - dragTx) / (double) TRACK_W);
                else dragging = null;
            }

            ctx.disableScissor();
        } finally {
            ctx.getMatrices().popMatrix();
            Ui.setAlpha(1f);
        }
    }
}
