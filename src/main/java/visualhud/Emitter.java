package visualhud;

import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Белые частицы, которые вылетают от краёв элемента наружу и плавно гаснут.
 * Круги рисуются в мелкой сетке (масштаб < 1) по дробным координатам, поэтому двигаются плавно,
 * а не прыгают по пикселям. Внутри панели (над текстом) частицы не появляются.
 */
public final class Emitter {
    private static final Random RNG = new Random();
    private static final int MAX = 28;

    private static final class P {
        float x, y, vx, vy, age, life, size;
    }

    private final List<P> list = new ArrayList<>();
    private long last = System.nanoTime();
    private float carry = 0f;

    public void draw(DrawContext ctx, int x, int y, int w, int h) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - last) / 1_000_000_000f);
        last = now;

        // чем больше периметр, тем больше частиц; но в меру
        float perimeter = 2f * (w + h);
        carry += dt * Math.min(7f, 1.6f + perimeter / 60f);
        while (carry >= 1f && list.size() < MAX) {
            carry -= 1f;
            list.add(spawn(x, y, w, h));
        }
        if (carry > 1f) carry = 1f;

        for (int i = list.size() - 1; i >= 0; i--) {
            P p = list.get(i);
            p.age += dt;
            if (p.age >= p.life) {
                list.remove(i);
                continue;
            }
            // лёгкое замедление и покачивание
            p.vx *= (1f - 0.6f * dt);
            p.vy *= (1f - 0.6f * dt);
            p.x += p.vx * dt + (float) Math.sin((p.age + p.size * 10f) * 4f) * 0.12f * dt * 10f;
            p.y += p.vy * dt;

            float t = p.age / p.life;
            float fadeIn = Math.min(1f, t / 0.15f);
            float fadeOut = 1f - t;
            int alpha = (int) (200f * fadeIn * fadeOut * fadeOut);
            if (alpha < 6) continue;
            drawDot(ctx, p.x, p.y, p.size * (1f - 0.35f * t), alpha);
        }
    }

    private static P spawn(int x, int y, int w, int h) {
        P p = new P();
        // точка на периметре, выбор стороны пропорционален её длине
        float r = RNG.nextFloat() * 2f * (w + h);
        float nx, ny;
        if (r < w) { p.x = x + r; p.y = y; nx = 0; ny = -1; }
        else if (r < w + h) { p.x = x + w; p.y = y + (r - w); nx = 1; ny = 0; }
        else if (r < 2 * w + h) { p.x = x + (r - w - h); p.y = y + h; nx = 0; ny = 1; }
        else { p.x = x; p.y = y + (r - 2 * w - h); nx = -1; ny = 0; }

        float speed = 5f + RNG.nextFloat() * 9f;
        float spread = (RNG.nextFloat() - 0.5f) * 0.9f;
        p.vx = (nx + spread * ny) * speed;
        p.vy = (ny - spread * nx) * speed - 2f;   // чуть вверх
        p.life = 1.0f + RNG.nextFloat() * 0.9f;
        p.size = 0.45f + RNG.nextFloat() * 0.35f; // ~2.5-4 px в диаметре
        return p;
    }

    /** Кружок радиусом 2 «единицы» (формы 3-5-5-5-3), масштабируется до нужного размера. */
    private static void drawDot(DrawContext ctx, float cx, float cy, float scale, int alpha) {
        int col = (alpha << 24) | 0xFFFFFF;
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(cx, cy);
        ctx.getMatrices().scale(scale, scale);
        ctx.fill(-1, -2, 2, -1, col);
        ctx.fill(-2, -1, 3, 2, col);
        ctx.fill(-1, 2, 2, 3, col);
        ctx.getMatrices().popMatrix();
    }
}
