package visualhud;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class VisualHudClient implements ClientModInitializer {
    private static final int MARGIN = 6;

    // Эффекты: панель покрупнее, текст помельче (только название и таймер)
    private static final float EFFECT_TEXT = 0.55f;
    private static final int EFFECT_ROW = 16;
    private static final int EFFECT_PAD_X = 8;
    private static final int EFFECT_PAD_Y = 5;
    private static final int EFFECT_MIN_W = 118;

    // Кулдауны: предметы поменьше
    private static final float CD_ICON = 0.6f;     // ~10x10
    private static final int CD_ROW = 13;
    private static final int CD_W = 46;
    private static final int GAP = 4;              // зазор между зельями и кулдаунами

    private final Map<String, Emitter> emitters = new HashMap<>();

    private Emitter fx(String key) {
        return emitters.computeIfAbsent(key, k -> new Emitter());
    }

    @Override
    public void onInitializeClient() {
        HudElementRegistry.replaceElement(VanillaHudElements.HOTBAR, prev -> this::renderHotbarAndOffhand);
        HudElementRegistry.replaceElement(VanillaHudElements.STATUS_EFFECTS, prev -> this::renderEffects);
        HudElementRegistry.replaceElement(VanillaHudElements.ARMOR_BAR, prev -> (ctx, tick) -> {});

        HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS,
                Identifier.of("visualhud", "info_bar"), this::renderInfoBar);
        HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS,
                Identifier.of("visualhud", "cooldowns"), this::renderCooldowns);
        HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS,
                Identifier.of("visualhud", "armor_durability"), this::renderArmor);
    }

    private static boolean hidden(MinecraftClient mc) {
        return mc.player == null || mc.options.hudHidden || mc.getDebugHud().shouldShowDebugHud();
    }

    // ---------- FPS и пинг (сверху слева) ----------
    private void renderInfoBar(DrawContext ctx, RenderTickCounter tick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (hidden(mc)) return;
        TextRenderer tr = mc.textRenderer;

        int ping = 0;
        if (mc.getNetworkHandler() != null) {
            PlayerListEntry e = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            if (e != null) ping = e.getLatency();
        }
        String s = "FPS " + mc.getCurrentFps() + "   |   ПИНГ " + ping + " мс";
        int w = Math.max(96, Ui.textWidth(tr, s) + 14);
        int h = 15;
        int x = MARGIN, y = MARGIN;

        Ui.softPanel(ctx, x, y, w, h, 4);
        Ui.text(ctx, tr, s, x + (w - Ui.textWidth(tr, s)) / 2, y + (h - Ui.textHeight(tr)) / 2 + 1, Ui.TEXT);
        fx("info").draw(ctx, x, y, w, h);
    }

    // ---------- Эффекты зелий (сверху справа) ----------
    private static String effectName(StatusEffectInstance e) {
        String name = e.getEffectType().value().getName().getString();
        if (e.getAmplifier() > 0) name += " " + (e.getAmplifier() + 1);
        return name;
    }

    private static String effectTime(StatusEffectInstance e) {
        return e.isInfinite() ? "∞" : formatTime(e.getDuration());
    }

    /** Размер панели эффектов {ширина, высота}; нужен и кулдаунам, чтобы встать рядом. */
    private int[] effectsSize(TextRenderer tr, Collection<StatusEffectInstance> effects) {
        if (effects.isEmpty()) return new int[]{0, 0};
        int w = EFFECT_MIN_W;
        for (StatusEffectInstance e : effects) {
            w = Math.max(w, EFFECT_PAD_X + Ui.textWidth(tr, effectName(e), EFFECT_TEXT)
                    + 12 + Ui.textWidth(tr, effectTime(e), EFFECT_TEXT) + EFFECT_PAD_X);
        }
        return new int[]{w, EFFECT_PAD_Y * 2 + effects.size() * EFFECT_ROW};
    }

    private void renderEffects(DrawContext ctx, RenderTickCounter tick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (hidden(mc)) return;
        TextRenderer tr = mc.textRenderer;
        Collection<StatusEffectInstance> effects = mc.player.getStatusEffects();
        if (effects.isEmpty()) return;

        int[] size = effectsSize(tr, effects);
        int w = size[0], h = size[1];
        int x = ctx.getScaledWindowWidth() - MARGIN - w;
        int y = MARGIN;

        Ui.softPanel(ctx, x, y, w, h, 5);
        int ry = y + EFFECT_PAD_Y;
        int textH = Ui.textHeight(tr, EFFECT_TEXT);
        for (StatusEffectInstance e : effects) {
            int ty = ry + (EFFECT_ROW - textH) / 2;
            Ui.text(ctx, tr, effectName(e), x + EFFECT_PAD_X, ty, Ui.TEXT, EFFECT_TEXT);
            String time = effectTime(e);
            Ui.text(ctx, tr, time, x + w - EFFECT_PAD_X - Ui.textWidth(tr, time, EFFECT_TEXT), ty, Ui.WHITE, EFFECT_TEXT);
            ry += EFFECT_ROW;
        }
        fx("effects").draw(ctx, x, y, w, h);
    }

    private static String formatTime(int ticks) {
        int sec = ticks / 20;
        return String.format("%d:%02d", sec / 60, sec % 60);
    }

    // ---------- Кулдауны (прямо под эффектами зелий) ----------
    private void renderCooldowns(DrawContext ctx, RenderTickCounter tick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (hidden(mc)) return;
        TextRenderer tr = mc.textRenderer;
        ClientPlayerEntity p = mc.player;
        float tp = tick.getTickProgress(false);

        Map<Item, Float> cds = new LinkedHashMap<>();
        for (int i = 0; i < p.getInventory().size(); i++) {
            ItemStack st = p.getInventory().getStack(i);
            if (st.isEmpty() || cds.containsKey(st.getItem())) continue;
            float pr = p.getItemCooldownManager().getCooldownProgress(st, tp);
            if (pr > 0f) cds.put(st.getItem(), pr);
        }
        if (cds.isEmpty()) return;

        int[] eff = effectsSize(tr, p.getStatusEffects());
        int w = CD_W;
        int h = 6 + cds.size() * CD_ROW;
        int x = ctx.getScaledWindowWidth() - MARGIN - w;
        int y = MARGIN + (eff[1] > 0 ? eff[1] + GAP : 0);

        Ui.softPanel(ctx, x, y, w, h, 5);
        int ry = y + 3;
        int iconPx = Math.round(16 * CD_ICON);
        int textH = Ui.textHeight(tr, Ui.TINY_SCALE);
        for (Map.Entry<Item, Float> en : cds.entrySet()) {
            Ui.item(ctx, en.getKey().getDefaultStack(), x + 4, ry + (CD_ROW - iconPx) / 2, CD_ICON);
            // вертикальная шкала оставшегося кулдауна
            int barH = 9;
            int filled = Math.round(barH * en.getValue());
            int bx = x + 4 + iconPx + 3;
            int by = ry + (CD_ROW - barH) / 2;
            ctx.fill(bx, by, bx + 1, by + barH, 0x30FFFFFF);
            ctx.fill(bx, by + barH - filled, bx + 1, by + barH, 0xB0FFFFFF);
            String t = Math.round(en.getValue() * 100) + "%";
            Ui.text(ctx, tr, t, bx + 4, ry + (CD_ROW - textH) / 2, Ui.TEXT, Ui.TINY_SCALE);
            ry += CD_ROW;
        }
        fx("cooldowns").draw(ctx, x, y, w, h);
    }

    // ---------- Хотбар и предмет во второй руке (снизу) ----------
    private void renderHotbarAndOffhand(DrawContext ctx, RenderTickCounter tick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (hidden(mc)) return;
        ClientPlayerEntity p = mc.player;
        TextRenderer tr = mc.textRenderer;

        int w = 9 * 20 + 10;
        int h = 24;
        int x = (ctx.getScaledWindowWidth() - w) / 2;
        int y = ctx.getScaledWindowHeight() - h - 2;

        Ui.softPanel(ctx, x, y, w, h, 8);

        int sel = p.getInventory().getSelectedSlot();
        for (int i = 0; i < 9; i++) {
            int sx = x + 5 + i * 20;
            if (i == sel) {
                Ui.roundRect(ctx, sx, y + 2, 20, 20, 5, 0x55FFFFFF);
            }
            ItemStack st = p.getInventory().getStack(i);
            if (!st.isEmpty()) {
                ctx.drawItem(st, sx + 2, y + 4);
                ctx.drawStackOverlay(tr, st, sx + 2, y + 4);
            }
        }
        fx("hotbar").draw(ctx, x, y, w, h);

        ItemStack off = p.getOffHandStack();
        if (!off.isEmpty()) {
            int r = 13;
            int cx = x - r - 8;
            int cy = y + h / 2;
            Ui.softCircle(ctx, cx, cy, r);
            ctx.drawItem(off, cx - 8, cy - 8);
            ctx.drawStackOverlay(tr, off, cx - 8, cy - 8);
            fx("offhand").draw(ctx, cx - r, cy - r, r * 2, r * 2);
        }
    }

    // ---------- Надетая броня и её прочность (снизу справа от хотбара) ----------
    private void renderArmor(DrawContext ctx, RenderTickCounter tick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (hidden(mc)) return;
        TextRenderer tr = mc.textRenderer;
        ClientPlayerEntity p = mc.player;

        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        ItemStack[] worn = new ItemStack[4];
        int n = 0;
        for (EquipmentSlot s : slots) {
            ItemStack st = p.getEquippedStack(s);
            if (!st.isEmpty()) worn[n++] = st;
        }
        if (n == 0) return;

        int slotW = 20;
        int w = 6 + n * slotW;
        int h = 32;
        int hotW = 9 * 20 + 10;
        int x = (ctx.getScaledWindowWidth() + hotW) / 2 + 8;
        int y = ctx.getScaledWindowHeight() - h - 2;

        Ui.softPanel(ctx, x, y, w, h, 6);
        int textH = Ui.textHeight(tr, Ui.TINY_SCALE);
        for (int i = 0; i < n; i++) {
            ItemStack st = worn[i];
            int sx = x + 3 + i * slotW;
            ctx.drawItem(st, sx + 2, y + 3);

            if (st.isDamageable()) {
                float frac = (st.getMaxDamage() - st.getDamage()) / (float) st.getMaxDamage();
                int color = frac > 0.5f ? 0xFFFFFF : frac > 0.2f ? 0xE8C060 : 0xE06060;
                int bx = sx + 2, bw = 16, by = y + 21;
                ctx.fill(bx, by, bx + bw, by + 2, 0x30FFFFFF);
                ctx.fill(bx, by, bx + Math.max(1, Math.round(bw * frac)), by + 2, 0xC0000000 | color);
                String t = Math.round(frac * 100) + "%";
                Ui.text(ctx, tr, t, sx + (slotW - Ui.textWidth(tr, t, Ui.TINY_SCALE)) / 2, y + 25, Ui.TEXT, Ui.TINY_SCALE);
            }
        }
        fx("armor").draw(ctx, x, y, w, h);
    }
}
