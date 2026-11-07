package dev.pulsehud;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.util.Identifier;

/**
 * All texture drawing goes through this class. If a Minecraft update changes the
 * DrawContext signatures, this is the only file that needs touching.
 *
 * The textures are smooth anti-aliased PNGs with "blur": true in their .mcmeta,
 * so the rounded corners stay round instead of turning into pixel stairs.
 */
public final class Gfx {
	public static final Identifier PANEL = Identifier.of(PulseHudClient.MOD_ID, "textures/gui/panel.png");
	public static final Identifier CIRCLE = Identifier.of(PulseHudClient.MOD_ID, "textures/gui/circle.png");
	public static final Identifier SLOT = Identifier.of(PulseHudClient.MOD_ID, "textures/gui/slot.png");
	public static final Identifier BAR = Identifier.of(PulseHudClient.MOD_ID, "textures/gui/bar.png");
	public static final Identifier DOT = Identifier.of(PulseHudClient.MOD_ID, "textures/gui/dot.png");

	public static final int WHITE = 0xFFFFFFFF;

	// panel.png is 64x64 with 16px rounded corners
	private static final int PANEL_SIZE = 64;
	private static final int PANEL_CORNER = 16;

	private Gfx() {}

	/** Draws a sub-rectangle of a texture, scaled to w*h. */
	public static void tex(DrawContext c, Identifier id, int x, int y, int w, int h,
						   int u, int v, int regionW, int regionH, int texW, int texH, int argb) {
		if (w <= 0 || h <= 0) return;
		c.drawTexture(RenderPipelines.GUI_TEXTURED, id, x, y, (float) u, (float) v, w, h,
			regionW, regionH, texW, texH, argb);
	}

	/** Draws a whole texture stretched to w*h. */
	public static void full(DrawContext c, Identifier id, int x, int y, int w, int h, int texSize, int argb) {
		tex(c, id, x, y, w, h, 0, 0, texSize, texSize, texSize, texSize, argb);
	}

	/**
	 * Draws a pill-shaped bar (bar.png is 64x16): two round end caps plus a stretched middle,
	 * so the ends stay perfectly round at any length.
	 */
	public static void bar(DrawContext c, int x, int y, int w, int h, int argb) {
		if (w <= 0 || h <= 0) return;
		int cap = Math.min(h / 2, w / 2);
		if (cap < 1) {
			tex(c, BAR, x, y, w, h, 0, 0, 64, 16, 64, 16, argb);
			return;
		}
		tex(c, BAR, x, y, cap, h, 0, 0, 8, 16, 64, 16, argb);
		tex(c, BAR, x + w - cap, y, cap, h, 56, 0, 8, 16, 64, 16, argb);
		tex(c, BAR, x + cap, y, w - 2 * cap, h, 8, 0, 48, 16, 64, 16, argb);
	}

	/**
	 * Draws a rounded panel of any size from panel.png using 9-slice scaling.
	 * The on-screen corner radius is min(wanted, half the smaller side), so a short
	 * wide panel becomes a perfect pill and a big one gets neat round corners.
	 */
	public static void panel(DrawContext c, int x, int y, int w, int h, int wantedRadius) {
		int r = Math.max(2, Math.min(wantedRadius, (Math.min(w, h) - 1) / 2));
		int s = PANEL_SIZE, k = PANEL_CORNER, mid = s - 2 * k;
		int mw = w - 2 * r, mh = h - 2 * r;

		// corners
		tex(c, PANEL, x, y, r, r, 0, 0, k, k, s, s, WHITE);
		tex(c, PANEL, x + w - r, y, r, r, s - k, 0, k, k, s, s, WHITE);
		tex(c, PANEL, x, y + h - r, r, r, 0, s - k, k, k, s, s, WHITE);
		tex(c, PANEL, x + w - r, y + h - r, r, r, s - k, s - k, k, k, s, s, WHITE);
		// edges
		tex(c, PANEL, x + r, y, mw, r, k, 0, mid, k, s, s, WHITE);
		tex(c, PANEL, x + r, y + h - r, mw, r, k, s - k, mid, k, s, s, WHITE);
		tex(c, PANEL, x, y + r, r, mh, 0, k, k, mid, s, s, WHITE);
		tex(c, PANEL, x + w - r, y + r, r, mh, s - k, k, k, mid, s, s, WHITE);
		// centre
		tex(c, PANEL, x + r, y + r, mw, mh, k, k, mid, mid, s, s, WHITE);
	}
}
