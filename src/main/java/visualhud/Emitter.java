package visualhud;

import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Белые кружки-частицы, которые медленно вылетают из элемента HUD. */
public final class Emitter {
    private static final Random RNG = new Random();

    private static final class P {
        float x, y, vx, vy, age, life;
        int radius;
    }

    private final List<P> list = new ArrayList<>();
    private long last = System.nanoTime();
    private float carry = 0f;

    public void draw(DrawContext ctx, int x, int y, int w, int h) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - last) / 1_000_000_000f);
        last = now;

        // сколько частиц в секунду: зависит от размера элемента
        carry += dt * (4f + w / 14f);
        while (carry >= 1f && list.size() < 40) {
            carry -= 1f;
            P p = new P();
            p.x = x + 2 + RNG.nextFloat() * Math.max(1, w - 4);
            p.y = y + RNG.nextFloat() * Math.max(1, h);
            p.vx = (RNG.nextFloat() - 0.5f) * 8f;
            p.vy = -(6f + RNG.nextFloat() * 10f);
            p.life = 0.8f + RNG.nextFloat() * 1.0f;
            p.radius = RNG.nextInt(3) == 0 ? 2 : 1;
            list.add(p);
        }
        if (carry > 1f) carry = 1f;

        for (int i = list.size() - 1; i >= 0; i--) {
            P p = list.get(i);
            p.age += dt;
            if (p.age >= p.life) {
                list.remove(i);
                continue;
            }
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            float t = p.age / p.life;
            int alpha = (int) (170 * (1f - t));
            Ui.circle(ctx, Math.round(p.x), Math.round(p.y), p.radius, (alpha << 24) | 0xFFFFFF);
        }
    }
}
