package net.vitrix.pvpislifemod.gametest;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import javax.imageio.ImageIO;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import net.minecraft.client.Minecraft;

import net.vitrix.pvpislifemod.PvpIsLifeModClient;
import net.vitrix.pvpislifemod.timing.HudMode;
import net.vitrix.pvpislifemod.timing.SampleKind;
import net.vitrix.pvpislifemod.timing.TimingEngine;
import net.vitrix.pvpislifemod.timing.TimingMode;

/**
 * Boots a real client, feeds the timing engine a handful of deterministic samples and screenshots
 * the top left corner. Starting the client at all proves that both mixins apply and that the HUD
 * element is registered; the screenshot proves the box renders the values.
 */
@SuppressWarnings("UnstableApiUsage")
public class PvpTimingClientGameTest implements FabricClientGameTest {
	private static final double MS = 1_000_000.0;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();

			context.runOnClient(client -> {
				feedSamples();
				checkEngineState(client);
			});

			context.waitTicks(3);

			// AUTO follows the last technique performed (the S tap), the second shot is locked to jump resets.
			cropPreview(context.takeScreenshot("pvpislifemod-hud-auto"), "build/pvpislifemod-preview-auto.png");

			context.runOnClient(client -> PvpIsLifeModClient.config().mode = HudMode.JUMP);
			context.waitTicks(3);
			cropPreview(context.takeScreenshot("pvpislifemod-hud-jump"), "build/pvpislifemod-preview-jump.png");
		}
	}

	/** Three jump resets and two W taps with known deltas. */
	private static void feedSamples() {
		TimingEngine engine = PvpIsLifeModClient.engine();
		engine.reset();

		long now = System.nanoTime();

		// Each pairing is resolved before the next hit exists, so every sample is unambiguous.

		// late jump: the hit landed 140 ms before the jump
		engine.onHit(true, now);
		engine.onJump(now + ms(140));
		engine.tick(now + ms(500));

		// perfect jump: 8 ms after the hit
		engine.onHit(true, now + ms(1_000));
		engine.onJump(now + ms(1_008));
		engine.tick(now + ms(1_500));

		// early jump: the jump happened 96 ms before the hit
		engine.onJump(now + ms(2_000));
		engine.onHit(true, now + ms(2_096));
		engine.tick(now + ms(2_500));

		// W tap 31 ms after a hit we dealt, held for 64 ms
		engine.onHit(false, now + ms(3_000));
		engine.onAction(TimingMode.WTAP, now + ms(3_031), 64.0, true, now + ms(3_500));
		engine.tick(now + ms(4_000));

		// second W tap, this one 58 ms late
		engine.onHit(true, now + ms(4_100));
		engine.onAction(TimingMode.WTAP, now + ms(4_158), 41.0, true, now + ms(4_500));
		engine.tick(now + ms(5_000));

		// S tap 22 ms after the hit, held for 70 ms
		engine.onHit(true, now + ms(5_200));
		engine.onAction(TimingMode.STAP, now + ms(5_222), 70.0, true, now + ms(5_600));
		engine.tick(now + ms(6_000));

		if (engine.stats(TimingMode.JUMP).total() != 3) {
			throw new IllegalStateException("expected 3 jump samples but got " + engine.stats(TimingMode.JUMP).total());
		}
	}

	private static void checkEngineState(Minecraft client) {
		TimingEngine engine = PvpIsLifeModClient.engine();

		if (engine.stats(TimingMode.JUMP).perfectCount() != 1) {
			throw new IllegalStateException("expected exactly one perfect jump, got " + engine.stats(TimingMode.JUMP).perfectCount());
		}

		if (engine.stats(TimingMode.JUMP).earlyCount() != 1) {
			throw new IllegalStateException("expected exactly one early jump");
		}

		if (engine.stats(TimingMode.JUMP).lateCount() != 1) {
			throw new IllegalStateException("expected exactly one late jump");
		}

		if (engine.stats(TimingMode.WTAP).last().kind() != SampleKind.PERFECT) {
			throw new IllegalStateException("expected the W tap to be perfect, got " + engine.stats(TimingMode.WTAP).last().kind());
		}

		if (client.player == null) {
			throw new IllegalStateException("client player is missing");
		}
	}

	/**
	 * Crops the top left corner of the screenshot and prints it as base64 so CI can republish it
	 * where it is readable.
	 */
	private static void cropPreview(Path screenshot, String outputPath) {
		try {
			BufferedImage image = ImageIO.read(screenshot.toFile());

			if (image == null) {
				System.out.println("PVPISLIFE_PREVIEW: could not read " + screenshot);
				return;
			}

			int width = Math.min(image.getWidth(), 520);
			int height = Math.min(image.getHeight(), 220);
			BufferedImage cropped = image.getSubimage(0, 0, width, height);

			Path output = Path.of(outputPath);
			Files.createDirectories(output.getParent() == null ? Path.of(".") : output.getParent());
			ImageIO.write(cropped, "png", output.toFile());

			System.out.println("PVPISLIFE_PREVIEW_FILE " + output.toAbsolutePath() + " (" + Files.size(output) + " bytes)");
			System.out.println("PVPISLIFE_PREVIEW_BASE64_BEGIN " + output.getFileName());
			System.out.println(Base64.getEncoder().encodeToString(Files.readAllBytes(output)));
			System.out.println("PVPISLIFE_PREVIEW_BASE64_END");
		} catch (Exception exception) {
			System.out.println("PVPISLIFE_PREVIEW: failed - " + exception);
		}
	}

	private static long ms(double value) {
		return (long) (value * MS);
	}
}
