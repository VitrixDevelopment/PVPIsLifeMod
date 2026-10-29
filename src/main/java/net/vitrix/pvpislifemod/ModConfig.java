package net.vitrix.pvpislifemod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;
import net.vitrix.pvpislifemod.timing.HudMode;

/**
 * Plain JSON config stored in {@code config/pvpislifemod.json}. Everything can also be changed in game
 * with the key binds, so the file is only needed for fine tuning.
 */
public final class ModConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** Which box is shown: AUTO follows the technique you performed last. */
	public HudMode mode = HudMode.AUTO;
	/** Master switch for the HUD box. */
	public boolean hudEnabled = true;
	/** How close to the hit (in ms) a reset has to be to count as perfect. */
	public double perfectWindowMs = 50.0;
	/** Show the last few attempts as a compact row of numbers. */
	public boolean showHistory = true;
	/** Show the session summary line. */
	public boolean showStats = true;
	/** Top left corner of the box, in GUI pixels. */
	public int x = 4;
	public int y = 4;

	public static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("pvpislifemod.json");
	}

	public static ModConfig load() {
		Path path = path();

		if (!Files.exists(path)) {
			return new ModConfig();
		}

		try {
			ModConfig config = GSON.fromJson(Files.readString(path), ModConfig.class);
			return config == null ? new ModConfig() : config;
		} catch (Exception exception) {
			PvpIsLifeModClient.LOGGER.warn("Could not read {}, using defaults", path, exception);
			return new ModConfig();
		}
	}

	public void save() {
		Path path = path();

		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(this));
		} catch (IOException exception) {
			PvpIsLifeModClient.LOGGER.warn("Could not write {}", path, exception);
		}
	}

	public double clampWindow(double windowMs) {
		// keep the value inside something sane so the HUD stays readable
		return Math.max(0.0, Math.min(500.0, windowMs));
	}
}
