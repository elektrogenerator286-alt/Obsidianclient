package dev.pulsehud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import net.minecraft.client.gl.RenderPipelines;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Draws every widget from the sketch:
 *  - FPS and ping (top left)
 *  - potion effects (top right)
 *  - item cooldowns (right side, under the effects)
 *  - custom hotbar (bottom centre), off-hand circle (left of it), armor durability (right of it)
 * Each panel has a white outline and sheds white particles (see Particles).
 */
public final class Hud {
	private static final int MARGIN = 6;
	private static final int GAP = 6;

	private static final int TEXT = 0xFFFFFFFF;
	private static final int TEXT_DIM = 0xFFB8B8B8;
	private static final int TEXT_BAD = 0xFFFF6B6B;

	private Hud() {}

	public static void render(DrawContext c, RenderTickCounter tickCounter) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (!PulseHudClient.enabled || mc.player == null || mc.world == null || mc.options.hudHidden) {
			return;
		}
		PlayerEntity player = mc.player;
		TextRenderer tr = mc.textRenderer;
		int sw = c.getScaledWindowWidth();
		int sh = c.getScaledWindowHeight();

		fpsAndPing(c, mc, tr);
		int effectsBottom = effects(c, mc, tr, sw);
		cooldowns(c, player, sw, effectsBottom);
		hotbarGroup(c, player, tr, sw, sh);

		Particles.updateAndDraw(c);
	}

	// ------------------------------------------------------------------ FPS / ping

	private static void fpsAndPing(DrawContext c, MinecraftClient mc, TextRenderer tr) {
		int ping = 0;
		if (mc.getNetworkHandler() != null && mc.player != null) {
			PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
			if (entry != null) ping = entry.getLatency();
		}
		String text = mc.getCurrentFps() + " FPS   |   " + ping + " ms";
		int w = tr.getWidth(text) + 18;
		int h = 18;
		int x = MARGIN, y = MARGIN;

		Gfx.panel(c, x, y, w, h, 9);
		c.drawText(tr, text, x + 9, y + (h - 8) / 2, TEXT, true);
		Particles.emitter(x, y, w, h);
	}

	// ------------------------------------------------------------------ Potion effects

	/** @return the y coordinate just below the panel (or the top margin if nothing is drawn) */
	private static int effects(DrawContext c, MinecraftClient mc, TextRenderer tr, int sw) {
		List<StatusEffectInstance> list = new ArrayList<>(mc.player.getStatusEffects());
		if (list.isEmpty()) return MARGIN;

		float tickRate = mc.world.getTickManager().getTickRate();
		int rowH = 22;
		int textMax = 0;
		List<Text> names = new ArrayList<>();
		List<Text> times = new ArrayList<>();
		for (StatusEffectInstance e : list) {
			MutableText name = Text.empty().append(e.getEffectType().value().getName());
			if (e.getAmplifier() > 0) {
				name.append(" ").append(Text.translatable("enchantment.level." + (e.getAmplifier() + 1)));
			}
			Text time = StatusEffectUtil.getDurationText(e, 1.0f, tickRate);
			names.add(name);
			times.add(time);
			textMax = Math.max(textMax, Math.max(tr.getWidth(name), tr.getWidth(time)));
		}

		int padX = 8, padY = 7;
		int w = padX * 2 + 18 + 6 + textMax;
		int h = padY * 2 + rowH * list.size() - (rowH - 18);
		int x = sw - MARGIN - w;
		int y = MARGIN;

		Gfx.panel(c, x, y, w, h, 8);

		for (int i = 0; i < list.size(); i++) {
			StatusEffectInstance e = list.get(i);
			int ry = y + padY + i * rowH;

			Optional<Identifier> sprite = e.getEffectType().getKey()
				.map(k -> k.getValue().withPrefixedPath("mob_effect/"));
			sprite.ifPresent(id -> c.drawGuiTexture(RenderPipelines.GUI_TEXTURED, id, x + padX, ry, 18, 18));

			boolean bad = e.getEffectType().value().getCategory() == StatusEffectCategory.HARMFUL;
			c.drawText(tr, names.get(i), x + padX + 24, ry, bad ? TEXT_BAD : TEXT, true);
			c.drawText(tr, times.get(i), x + padX + 24, ry + 9, TEXT_DIM, true);
		}

		Particles.emitter(x, y, w, h);
		return y + h;
	}

	// ------------------------------------------------------------------ Item cooldowns

	private static void cooldowns(DrawContext c, PlayerEntity player, int sw, int topY) {
		ItemCooldownManager cm = player.getItemCooldownManager();
		PlayerInventory inv = player.getInventory();

		// one row per item type that is on cooldown (stacks of the same item share a cooldown)
		Map<Item, ItemStack> found = new LinkedHashMap<>();
		for (int i = 0; i < inv.size(); i++) {
			ItemStack s = inv.getStack(i);
			if (!s.isEmpty() && !found.containsKey(s.getItem()) && cm.isCoolingDown(s)) {
				found.put(s.getItem(), s);
				if (found.size() >= 6) break;
			}
		}
		if (found.isEmpty()) return;

		int rowH = 22;
		int padX = 8, padY = 7;
		int barW = 44;
		int w = padX * 2 + 16 + 8 + barW;
		int h = padY * 2 + rowH * found.size() - (rowH - 16);
		int x = sw - MARGIN - w;
		int y = (topY == MARGIN ? MARGIN : topY + GAP);

		Gfx.panel(c, x, y, w, h, 8);

		int i = 0;
		for (ItemStack s : found.values()) {
			int ry = y + padY + i * rowH;
			c.drawItem(s, x + padX, ry);

			float prog = Math.max(0f, Math.min(1f, cm.getCooldownProgress(s, 0f))); // 1 = just started
			int bx = x + padX + 16 + 8;
			int by = ry + 6;
			Gfx.bar(c, bx, by, barW, 4, 0x55FFFFFF);        // track
			int fillW = Math.max(4, Math.round(barW * prog));
			Gfx.bar(c, bx, by, fillW, 4, Gfx.WHITE);         // remaining
			i++;
		}
		Particles.emitter(x, y, w, h);
	}

	// ------------------------------------------------------------------ Hotbar + off-hand + armor

	private static void hotbarGroup(DrawContext c, PlayerEntity player, TextRenderer tr, int sw, int sh) {
		PlayerInventory inv = player.getInventory();

		// ---- hotbar
		int slotW = 20;
		int padX = 8;
		int hw = slotW * 9 + padX * 2;
		int hh = 22;
		int hx = (sw - hw) / 2;
		int hy = sh - hh - 2;

		Gfx.panel(c, hx, hy, hw, hh, 11);

		// thin dividers between slots
		for (int i = 1; i < 9; i++) {
			int dx = hx + padX + i * slotW;
			c.fill(dx, hy + 5, dx + 1, hy + hh - 5, 0x2EFFFFFF);
		}

		// selected slot
		int sel = inv.getSelectedSlot();
		Gfx.full(c, Gfx.SLOT, hx + padX + sel * slotW + 1, hy + 2, slotW - 2, hh - 4, 32, Gfx.WHITE);

		// items
		for (int i = 0; i < 9; i++) {
			ItemStack s = inv.getStack(i);
			if (s.isEmpty()) continue;
			int ix = hx + padX + i * slotW + (slotW - 16) / 2;
			int iy = hy + (hh - 16) / 2;
			c.drawItem(s, ix, iy);
			c.drawStackOverlay(tr, s, ix, iy);
		}
		Particles.emitter(hx, hy, hw, hh);

		// ---- off-hand circle (only if something is in the off hand)
		ItemStack off = player.getOffHandStack();
		if (!off.isEmpty()) {
			int d = 26;
			int ox = hx - GAP - d;
			int oy = hy + (hh - d) / 2;
			Gfx.full(c, Gfx.CIRCLE, ox, oy, d, d, 32, Gfx.WHITE);
			int ix = ox + (d - 16) / 2;
			int iy = oy + (d - 16) / 2;
			c.drawItem(off, ix, iy);
			c.drawStackOverlay(tr, off, ix, iy);
			Particles.emitter(ox, oy, d, d);
		}

		// ---- armor durability
		armor(c, player, tr, sw, hx + hw + GAP, hy, hh);
	}

	private static void armor(DrawContext c, PlayerEntity player, TextRenderer tr, int sw, int x, int y, int h) {
		EquipmentSlot[] order = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
		List<ItemStack> pieces = new ArrayList<>();
		for (EquipmentSlot slot : order) {
			ItemStack s = player.getEquippedStack(slot);
			if (!s.isEmpty()) pieces.add(s);
		}
		if (pieces.isEmpty()) return;

		float scale = 0.75f;
		int icon = Math.round(16 * scale);   // 12 px
		int cellGap = 7;
		int padX = 8;

		int[] cellW = new int[pieces.size()];
		String[] label = new String[pieces.size()];
		int total = 0;
		for (int i = 0; i < pieces.size(); i++) {
			ItemStack s = pieces.get(i);
			label[i] = s.isDamageable()
				? Math.max(0, Math.round(100f * (s.getMaxDamage() - s.getDamage()) / s.getMaxDamage())) + ""
				: "-";
			cellW[i] = icon + 3 + Math.round(tr.getWidth(label[i]) * scale);
			total += cellW[i];
		}
		total += cellGap * (pieces.size() - 1);

		int w = total + padX * 2;
		int px = Math.min(x, sw - MARGIN - w);
		Matrix3x2fStack m = c.getMatrices();

		Gfx.panel(c, px, y, w, h, 11);

		int cx = px + padX;
		for (int i = 0; i < pieces.size(); i++) {
			ItemStack s = pieces.get(i);

			m.pushMatrix();
			m.translate(cx, y + (h - icon) / 2f);
			m.scale(scale, scale);
			c.drawItem(s, 0, 0);
			m.popMatrix();

			int color = TEXT;
			if (s.isDamageable()) {
				float left = (float) (s.getMaxDamage() - s.getDamage()) / s.getMaxDamage();
				if (left < 0.15f) color = TEXT_BAD;
				else if (left < 0.4f) color = 0xFFFFD36B;
			}
			m.pushMatrix();
			m.translate(cx + icon + 3, y + (h - 8 * scale) / 2f);
			m.scale(scale, scale);
			c.drawText(tr, label[i], 0, 0, color, true);
			m.popMatrix();

			cx += cellW[i] + cellGap;
		}
		Particles.emitter(px, y, w, h);
	}
}
