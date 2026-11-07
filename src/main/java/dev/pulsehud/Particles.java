package dev.pulsehud;

import net.minecraft.client.gui.DrawContext;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Small white dots that drift away from the edges of every HUD panel and fade out.
 */
public final class Particles {
	private static final int MAX = 220;
	private static final Random RNG = new Random();

	private static final class P {
		float x, y, vx, vy, age, life, size;
	}

	private static final List<P> LIST = new ArrayList<>();
	private static final List<int[]> EMITTERS = new ArrayList<>();
	private static long lastNanos = System.nanoTime();
	private static float spawnDebt = 0f;

	private Particles() {}

	/** Registers a panel rectangle (x, y, w, h) that should emit particles this frame. */
	public static void emitter(int x, int y, int w, int h) {
		EMITTERS.add(new int[]{x, y, w, h});
	}

	/** Advances the simulation and draws the particles. Call once per frame, after all panels. */
	public static void updateAndDraw(DrawContext c) {
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - lastNanos) / 1_000_000_000f);
		lastNanos = now;

		// spawn: roughly one particle per 22 px of perimeter per second
		float perimeter = 0;
		for (int[] r : EMITTERS) perimeter += 2f * (r[2] + r[3]);
		spawnDebt += perimeter / 22f * dt;
		while (spawnDebt >= 1f && LIST.size() < MAX && !EMITTERS.isEmpty()) {
			spawnDebt -= 1f;
			spawnOne(pickEmitter(perimeter));
		}
		if (spawnDebt > 4f) spawnDebt = 0f;
		EMITTERS.clear();

		Matrix3x2fStack m = c.getMatrices();
		for (int i = LIST.size() - 1; i >= 0; i--) {
			P p = LIST.get(i);
			p.age += dt;
			if (p.age >= p.life) {
				LIST.remove(i);
				continue;
			}
			p.x += p.vx * dt;
			p.y += p.vy * dt;

			float t = p.age / p.life;                       // 0 -> 1
			float alphaF = (float) Math.sin(Math.PI * Math.min(1f, t * 1.15f)); // fade in and out
			int a = Math.max(0, Math.min(255, (int) (alphaF * 200)));
			float sz = p.size * (1f - 0.35f * t);

			m.pushMatrix();
			m.translate(p.x - sz / 2f, p.y - sz / 2f);
			m.scale(sz / 8f, sz / 8f);
			Gfx.tex(c, Gfx.DOT, 0, 0, 8, 8, 0, 0, 16, 16, 16, 16, (a << 24) | 0xFFFFFF);
			m.popMatrix();
		}
	}

	private static int[] pickEmitter(float perimeter) {
		float pick = RNG.nextFloat() * perimeter;
		for (int[] r : EMITTERS) {
			pick -= 2f * (r[2] + r[3]);
			if (pick <= 0) return r;
		}
		return EMITTERS.get(EMITTERS.size() - 1);
	}

	private static void spawnOne(int[] r) {
		float x = r[0], y = r[1], w = r[2], h = r[3];
		float pos = RNG.nextFloat() * 2f * (w + h);
		P p = new P();
		float nx, ny; // outward normal
		if (pos < w) {                    // top
			p.x = x + pos; p.y = y; nx = 0; ny = -1;
		} else if (pos < w + h) {         // right
			p.x = x + w; p.y = y + (pos - w); nx = 1; ny = 0;
		} else if (pos < 2 * w + h) {     // bottom
			p.x = x + (pos - w - h); p.y = y + h; nx = 0; ny = 1;
		} else {                          // left
			p.x = x; p.y = y + (pos - 2 * w - h); nx = -1; ny = 0;
		}
		float speed = 5f + RNG.nextFloat() * 11f;
		p.vx = nx * speed + (RNG.nextFloat() - 0.5f) * 6f;
		p.vy = ny * speed + (RNG.nextFloat() - 0.5f) * 6f - 2f; // slight upward drift
		p.life = 0.9f + RNG.nextFloat() * 1.1f;
		p.size = 1.6f + RNG.nextFloat() * 2.4f;
		LIST.add(p);
	}
}
