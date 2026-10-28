package net.vitrix.pvpislifemod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import net.vitrix.pvpislifemod.hud.TimingHud;
import net.vitrix.pvpislifemod.timing.HudMode;
import net.vitrix.pvpislifemod.timing.TapDetector;
import net.vitrix.pvpislifemod.timing.TimingEngine;
import net.vitrix.pvpislifemod.timing.TimingMode;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Client entry point. Everything interesting happens here:
 * <ul>
 *     <li>hits come in through {@link #onLivingDamage(LivingEntity, DamageSource)} (see {@code LivingEntityMixin})</li>
 *     <li>key presses come in through {@link #onKeyMappingChanged(KeyMapping, boolean)} (see {@code KeyMappingMixin})</li>
 *     <li>{@link #onEndTick(Minecraft)} advances the engine and handles the mod's own key binds</li>
 * </ul>
 */
public final class PvpIsLifeModClient implements ClientModInitializer {
	public static final String MOD_ID = "pvpislifemod";
	public static final Logger LOGGER = LogManager.getLogger("PVPIsLife-Timing");

	/** Perfect window presets, cycled with the window key bind. */
	private static final double[] WINDOWS = {25.0, 50.0, 75.0, 100.0, 150.0, 200.0};

	// GLFW key codes, kept as plain ints so no LWJGL import is needed.
	private static final int KEY_LEFT_BRACKET = 91;
	private static final int KEY_RIGHT_BRACKET = 93;
	private static final int KEY_BACKSLASH = 92;
	private static final int KEY_R = 82;

	private static ModConfig config = new ModConfig();
	private static final TimingEngine ENGINE = new TimingEngine();
	private static final TapDetector TAPS = new TapDetector();
	private static final TimingHud HUD = new TimingHud();

	private static KeyMapping cycleModeKey;
	private static KeyMapping toggleHudKey;
	private static KeyMapping cycleWindowKey;
	private static KeyMapping resetStatsKey;

	private static ClientLevel lastLevel;

	@Override
	public void onInitializeClient() {
		config = ModConfig.load();
		config.perfectWindowMs = config.clampWindow(config.perfectWindowMs);
		ENGINE.setPerfectWindowMs(config.perfectWindowMs);

		registerKeyBinds();

		HudElementRegistry.attachElementBefore(
				VanillaHudElements.CHAT,
				Identifier.fromNamespaceAndPath(MOD_ID, "timing_box"),
				HUD
		);

		ClientTickEvents.END_CLIENT_TICK.register(PvpIsLifeModClient::onEndTick);

		LOGGER.info("PVPIsLife Timing ready (perfect window {} ms, mode {})", config.perfectWindowMs, config.mode);
	}

	private static void registerKeyBinds() {
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));

		toggleHudKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.pvpislifemod.toggle_hud", KEY_LEFT_BRACKET, category));
		cycleModeKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.pvpislifemod.cycle_mode", KEY_RIGHT_BRACKET, category));
		cycleWindowKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.pvpislifemod.cycle_window", KEY_BACKSLASH, category));
		resetStatsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.pvpislifemod.reset_stats", KEY_R, category));
	}

	// ------------------------------------------------------------------
	// ticking / key binds
	// ------------------------------------------------------------------

	private static void onEndTick(Minecraft minecraft) {
		long now = System.nanoTime();
		TAPS.tick(now);
		ENGINE.tick(now);

		boolean dirty = false;

		while (toggleHudKey != null && toggleHudKey.consumeClick()) {
			config.hudEnabled = !config.hudEnabled;
			dirty = true;
		}

		while (cycleModeKey != null && cycleModeKey.consumeClick()) {
			config.mode = config.mode.next();
			dirty = true;
		}

		while (cycleWindowKey != null && cycleWindowKey.consumeClick()) {
			config.perfectWindowMs = nextWindow(config.perfectWindowMs);
			ENGINE.setPerfectWindowMs(config.perfectWindowMs);
			dirty = true;
		}

		while (resetStatsKey != null && resetStatsKey.consumeClick()) {
			ENGINE.reset();
			TAPS.reset();
		}

		if (dirty) {
			config.save();
		}

		if (minecraft.level != lastLevel) {
			lastLevel = minecraft.level;
			TAPS.reset();
			ENGINE.reset();
		}
	}

	private static double nextWindow(double current) {
		for (double window : WINDOWS) {
			if (window > current + 0.5) {
				return window;
			}
		}

		return WINDOWS[0];
	}

	// ------------------------------------------------------------------
	// events fed in by the mixins
	// ------------------------------------------------------------------

	/**
	 * Called from {@code KeyMapping#setDown} right before the new state is applied.
	 */
	public static void onKeyMappingChanged(KeyMapping mapping, boolean down) {
		Minecraft minecraft = Minecraft.getInstance();

		if (minecraft == null || minecraft.player == null || minecraft.gui == null) {
			return;
		}

		// Only trust transitions made while playing. Opening a screen or losing window focus makes the
		// game release / re-apply every key at once, which must never be mistaken for a real tap.
		if (minecraft.mouseHandler == null || !minecraft.mouseHandler.isMouseGrabbed() || minecraft.gui.screen() != null) {
			return;
		}

		Options options = minecraft.options;
		LocalPlayer player = minecraft.player;
		long now = System.nanoTime();

		if (mapping == options.keyJump) {
			// Only count jumps that actually start from the ground; mid air jump presses do nothing.
			if (down && player.onGround()) {
				ENGINE.onJump(now);
			}

			return;
		}

		if (mapping == options.keyUp) {
			if (down) {
				TapDetector.Tap tap = TAPS.onWPress(now);

				if (tap != null) {
					ENGINE.onAction(TimingMode.WTAP, tap.startNanos(), tap.holdMs(), true, now);
				}
			} else if (player.isSprinting() || player.input.keyPresses.forward()) {
				// The tap starts when W is released; it only becomes a tap if W is pressed again soon.
				TAPS.onWRelease(now);
			}

			return;
		}

		if (mapping == options.keyDown) {
			if (down) {
				if (options.keyUp.isDown() || player.isSprinting() || player.input.keyPresses.forward()) {
					TAPS.onSPress(now);
				}
			} else {
				TapDetector.Tap tap = TAPS.onSRelease(now);

				if (tap != null) {
					ENGINE.onAction(TimingMode.STAP, tap.startNanos(), tap.holdMs(), true, now);
				}
			}
		}
	}

	/**
	 * Called from {@code LivingEntity#handleDamageEvent}, which runs on the client when a damage packet arrives.
	 */
	public static void onLivingDamage(LivingEntity entity, DamageSource source) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft == null ? null : minecraft.player;

		if (player == null) {
			return;
		}

		Entity attacker = source.getEntity();
		Entity direct = source.getDirectEntity();

		// Only direct melee hits: projectiles and environmental damage are ignored.
		if (!(attacker instanceof LivingEntity) || direct != attacker) {
			return;
		}

		long now = System.nanoTime();

		if (entity == player) {
			ENGINE.onHit(true, now);
		} else if (attacker == player && entity instanceof Player) {
			ENGINE.onHit(false, now);
		}
	}

	// ------------------------------------------------------------------
	// accessors used by the HUD
	// ------------------------------------------------------------------

	public static ModConfig config() {
		return config;
	}

	public static TimingEngine engine() {
		return ENGINE;
	}
}
