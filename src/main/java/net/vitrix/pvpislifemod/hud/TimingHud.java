package net.vitrix.pvpislifemod.hud;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import net.vitrix.pvpislifemod.ClientCompat;
import net.vitrix.pvpislifemod.ModConfig;
import net.vitrix.pvpislifemod.PvpIsLifeModClient;
import net.vitrix.pvpislifemod.timing.HudMode;
import net.vitrix.pvpislifemod.timing.ModeStats;
import net.vitrix.pvpislifemod.timing.SampleKind;
import net.vitrix.pvpislifemod.timing.TimingEngine;
import net.vitrix.pvpislifemod.timing.TimingMode;
import net.vitrix.pvpislifemod.timing.TimingSample;

/**
 * The timing box in the top left corner.
 *
 * <p>The box follows the technique you performed last while in {@link HudMode#AUTO}, and can be
 * locked to a single technique with the mode key bind.</p>
 */
public final class TimingHud implements HudElement {
	private static final int PADDING = 5;
	private static final int LINE_HEIGHT = 10;

	private static final int BACKGROUND = 0xC0101014;
	private static final int TEXT = 0xFFE8E8E8;
	private static final int DIM = 0xFF9AA0A6;

	private static final double SAMPLE_VISIBLE_MS = 10_000.0;
	private static final double FLASH_MS = 350.0;
	private static final int MAX_CONSECUTIVE_FAILURES = 20;

	private int failures;

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		// A HUD element must never be able to crash the game. A handful of failures in a row means
		// something is systematically wrong, so the box keeps out of the way after that.
		if (this.failures >= MAX_CONSECUTIVE_FAILURES) {
			return;
		}

		try {
			this.render(graphics);
			this.failures = 0;
		} catch (Throwable throwable) {
			if (this.failures++ == 0) {
				PvpIsLifeModClient.LOGGER.error("Timing HUD failed to render, hiding it after {} more errors", MAX_CONSECUTIVE_FAILURES, throwable);
			}
		}
	}

	private void render(GuiGraphicsExtractor graphics) {
		ModConfig config = PvpIsLifeModClient.config();
		Minecraft minecraft = Minecraft.getInstance();

		if (config == null || !config.hudEnabled || minecraft == null || minecraft.player == null || minecraft.level == null) {
			return;
		}

		if (ClientCompat.isScreenOpen(minecraft)) {
			return;
		}

		// The vanilla F3 overlay owns the top left corner, do not fight it for space.
		if (ClientCompat.isDebugOverlayShown(minecraft)) {
			return;
		}

		Font font = minecraft.font;
		TimingEngine engine = PvpIsLifeModClient.engine();
		long now = System.nanoTime();

		TimingMode mode = resolveMode(config, engine);
		ModeStats stats = engine.stats(mode);
		TimingSample last = stats.last();
		boolean fresh = last != null && last.ageMs(now) <= SAMPLE_VISIBLE_MS;

		List<Row> rows = this.buildRows(config, engine, mode, stats, last, fresh, now);
		int contentWidth = 0;

		for (Row row : rows) {
			contentWidth = Math.max(contentWidth, row.width(font));
		}

		int width = contentWidth + PADDING * 2;
		int height = rows.size() * LINE_HEIGHT + PADDING * 2 - 1;
		int x = Math.max(0, config.x);
		int y = Math.max(0, config.y);

		graphics.fill(x, y, x + width, y + height, BACKGROUND);
		graphics.outline(x, y, width, height, borderColor(mode, engine, last, fresh, now));

		int textY = y + PADDING;

		for (Row row : rows) {
			row.draw(graphics, font, x + PADDING, textY);
			textY += LINE_HEIGHT;
		}
	}

	private List<Row> buildRows(ModConfig config, TimingEngine engine, TimingMode mode, ModeStats stats,
			TimingSample last, boolean fresh, long now) {
		List<Row> rows = new ArrayList<>();

		// 1) title, plus a small "AUTO" marker while following the last action
		List<Seg> title = new ArrayList<>();
		title.add(new Seg(mode.hudLabel(), mode.color()));

		if (config.mode == HudMode.AUTO) {
			title.add(new Seg("  AUTO", DIM));
		} else {
			title.add(new Seg("  LOCKED", DIM));
		}

		rows.add(new Row(title));

		// 2) the big result
		if (fresh) {
			rows.add(new Row(List.of(new Seg(last.headline(), last.kind().color()))));
		} else if (stats.total() > 0) {
			rows.add(new Row(List.of(new Seg("no recent reset", TEXT))));
		} else {
			rows.add(new Row(List.of(new Seg("waiting for a hit", DIM))));
		}

		// 3) detail line: tap duration + which hit was used as reference
		if (fresh) {
			StringBuilder detail = new StringBuilder();

			if (last.hasHold()) {
				detail.append("tap ").append(Math.round(last.holdMs())).append(" ms · ");
			}

			if (config.mode == HudMode.AUTO && mode != TimingMode.JUMP) {
				detail.append(last.referenceTaken() ? "after hit taken" : "after your hit");
			} else {
				detail.append(last.referenceTaken() ? "hit taken" : "hit dealt");
			}

			rows.add(new Row(List.of(new Seg(detail.toString(), DIM))));
		}

		// 4) recent attempts
		if (config.showHistory && stats.recent().size() >= 2) {
			List<Seg> history = new ArrayList<>();
			history.add(new Seg("last ", DIM));

			List<TimingSample> recent = stats.recent();

			for (int i = 0; i < recent.size(); i++) {
				TimingSample sample = recent.get(i);

				if (i > 0) {
					history.add(new Seg(" ", DIM));
				}

				history.add(new Seg(sample.deltaText(), sample.kind().color()));
			}

			rows.add(new Row(history));
		}

		// 5) summary line, or the controls hint until the first sample exists
		if (config.showStats && stats.total() > 0) {
			String summary = stats.total() + " resets · " + Math.round(stats.perfectRate()) + "% perfect · avg "
					+ Math.round(stats.averageAbsError()) + " ms";
			rows.add(new Row(List.of(new Seg(summary, DIM))));
		} else {
			rows.add(new Row(List.of(new Seg("] mode   [ hud   \\ window", DIM))));
		}

		return rows;
	}

	private static int borderColor(TimingMode mode, TimingEngine engine, TimingSample last, boolean fresh, long now) {
		if (fresh && last.kind() == SampleKind.PERFECT && last.ageMs(now) <= FLASH_MS) {
			return SampleKind.PERFECT.color();
		}

		double sinceHit = engine.sinceLastTakenMs(now);

		if (sinceHit >= 0.0 && sinceHit <= FLASH_MS) {
			// you just got hit - the box lights up so you can see the moment you are timing against
			return 0xFFFFFFFF;
		}

		return mode.color();
	}

	private static TimingMode resolveMode(ModConfig config, TimingEngine engine) {
		TimingMode locked = config.mode.timingMode();
		return locked != null ? locked : engine.lastMode();
	}

	private record Seg(String text, int color) {
	}

	private record Row(List<Seg> segments) {
		private int width(Font font) {
			int width = 0;

			for (Seg segment : this.segments) {
				width += font.width(segment.text());
			}

			return width;
		}

		private void draw(GuiGraphicsExtractor graphics, Font font, int x, int y) {
			int cursor = x;

			for (Seg segment : this.segments) {
				graphics.text(font, segment.text(), cursor, y, segment.color());
				cursor += font.width(segment.text());
			}
		}
	}
}
