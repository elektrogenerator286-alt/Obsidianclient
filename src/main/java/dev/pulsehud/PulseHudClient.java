package dev.pulsehud;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class PulseHudClient implements ClientModInitializer {
	public static final String MOD_ID = "pulsehud";

	/** Master switch. When false the vanilla HUD is shown untouched. */
	public static boolean enabled = true;

	private static KeyBinding toggleKey;

	@Override
	public void onInitializeClient() {
		KeyBinding.Category category = KeyBinding.Category.create(Identifier.of(MOD_ID, "main"));
		toggleKey = KeyBindingHelper.registerKeyBinding(
			new KeyBinding("key.pulsehud.toggle", GLFW.GLFW_KEY_RIGHT_BRACKET, category)
		);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (toggleKey.wasPressed()) {
				enabled = !enabled;
			}
		});

		// Hide the vanilla hotbar (it also draws the off-hand slot) and the vanilla
		// potion-effect icons while Pulse HUD is on. When it is off they render as usual.
		HudElementRegistry.replaceElement(VanillaHudElements.HOTBAR, original -> (context, tickCounter) -> {
			if (!enabled) original.render(context, tickCounter);
		});
		HudElementRegistry.replaceElement(VanillaHudElements.STATUS_EFFECTS, original -> (context, tickCounter) -> {
			if (!enabled) original.render(context, tickCounter);
		});

		// Our own HUD, drawn right after the vanilla status effects (above health/xp, below chat).
		HudElementRegistry.attachElementAfter(
			VanillaHudElements.STATUS_EFFECTS,
			Identifier.of(MOD_ID, "hud"),
			Hud::render
		);
	}
}
