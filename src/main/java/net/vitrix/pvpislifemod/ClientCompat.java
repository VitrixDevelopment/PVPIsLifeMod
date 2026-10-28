package net.vitrix.pvpislifemod;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.minecraft.client.Minecraft;

/**
 * The GUI plumbing moved around inside the 26.x line: {@code Minecraft.screen} became
 * {@code Minecraft.gui.screen()} and {@code Minecraft.getDebugOverlay()} became
 * {@code Minecraft.gui.hud.getDebugOverlay()} in 26.2. Both are looked up reflectively once,
 * so the same jar keeps working on 26.1, 26.2 and 26.3.
 */
final class ClientCompat {
	private static boolean resolved;
	private static Field screenField;
	private static Field guiField;
	private static Method guiScreenMethod;
	private static Method debugOverlayMethod;
	private static Field hudField;
	private static Method hudDebugOverlayMethod;
	private static Method showDebugScreenMethod;

	private ClientCompat() {
	}

	static boolean isScreenOpen(Minecraft minecraft) {
		resolve();

		try {
			if (screenField != null) {
				return screenField.get(minecraft) != null;
			}

			if (guiField != null && guiScreenMethod != null) {
				Object gui = guiField.get(minecraft);
				return gui != null && guiScreenMethod.invoke(gui) != null;
			}
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			// fall through - an unknown layout simply means "no screen"
		}

		return false;
	}

	static boolean isDebugOverlayShown(Minecraft minecraft) {
		resolve();

		try {
			Object overlay = null;

			if (debugOverlayMethod != null) {
				overlay = debugOverlayMethod.invoke(minecraft);
			} else if (guiField != null && hudField != null && hudDebugOverlayMethod != null) {
				Object gui = guiField.get(minecraft);
				Object hud = gui == null ? null : hudField.get(gui);
				overlay = hud == null ? null : hudDebugOverlayMethod.invoke(hud);
			}

			if (overlay != null && showDebugScreenMethod != null) {
				Object shown = showDebugScreenMethod.invoke(overlay);
				return shown instanceof Boolean visible && visible;
			}
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			// ignore, the check is cosmetic
		}

		return false;
	}

	private static synchronized void resolve() {
		if (resolved) {
			return;
		}

		resolved = true;

		screenField = field(Minecraft.class, "screen");
		guiField = field(Minecraft.class, "gui");

		if (guiField != null) {
			guiScreenMethod = method(guiField.getType(), "screen");
			hudField = field(guiField.getType(), "hud");

			if (hudField != null) {
				hudDebugOverlayMethod = method(hudField.getType(), "getDebugOverlay");
			}
		}

		debugOverlayMethod = method(Minecraft.class, "getDebugOverlay");

		Class<?> overlayType = null;

		if (debugOverlayMethod != null) {
			overlayType = debugOverlayMethod.getReturnType();
		} else if (hudDebugOverlayMethod != null) {
			overlayType = hudDebugOverlayMethod.getReturnType();
		}

		if (overlayType != null) {
			showDebugScreenMethod = method(overlayType, "showDebugScreen");
		}
	}

	private static Field field(Class<?> owner, String name) {
		try {
			Field field = owner.getField(name);
			field.setAccessible(true);
			return field;
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			return null;
		}
	}

	private static Method method(Class<?> owner, String name) {
		try {
			Method method = owner.getMethod(name);
			method.setAccessible(true);
			return method;
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			return null;
		}
	}
}
