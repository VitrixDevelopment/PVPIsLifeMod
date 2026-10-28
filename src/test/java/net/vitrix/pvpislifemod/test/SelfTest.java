package net.vitrix.pvpislifemod.test;

import net.vitrix.pvpislifemod.timing.ModeStats;
import net.vitrix.pvpislifemod.timing.SampleKind;
import net.vitrix.pvpislifemod.timing.TapDetector;
import net.vitrix.pvpislifemod.timing.TimingEngine;
import net.vitrix.pvpislifemod.timing.TimingMode;
import net.vitrix.pvpislifemod.timing.TimingSample;

/**
 * Plain-Java self test for the timing engine. Run with {@code java tools/RunSelfTest.java}.
 * Not shipped in the mod jar.
 */
public final class SelfTest {
	private static int checks;
	private static int failures;

	public static void main(String[] args) {
		lateJump();
		perfectJump();
		earlyJump();
		noHitNoSample();
		dealtHitDoesNotCountForJump();
		dealtHitCountsForWTap();
		outsidePairWindow();
		hitUsedOncePerMode();
		oneHitOnlyOneSamplePerMode();
		perfectWindowBoundary();
		nearestHitWins();
		statsAggregation();
		tapDetection();
		resetClearsEverything();

		System.out.println();
		System.out.println(checks + " checks, " + failures + " failure(s)");

		if (failures > 0) {
			System.exit(1);
		}
	}

	// ------------------------------------------------------------------

	private static void lateJump() {
		TimingEngine engine = new TimingEngine();
		engine.onHit(true, 0);
		engine.onJump(ms(80));
		engine.tick(ms(80 + TimingEngine.PAIR_WINDOW_MS + 1));

		TimingSample sample = single(engine, TimingMode.JUMP);
		check("late jump delta", sample != null && near(sample.deltaMs(), 80.0), sample);
		check("late jump kind", sample != null && sample.kind() == SampleKind.LATE, sample);
		check("late jump reference", sample != null && sample.referenceTaken(), sample);
	}

	private static void perfectJump() {
		TimingEngine engine = new TimingEngine();
		engine.onHit(true, 0);
		engine.onJump(ms(20));
		engine.tick(ms(20 + TimingEngine.PAIR_WINDOW_MS + 1));

		TimingSample sample = single(engine, TimingMode.JUMP);
		check("perfect jump kind", sample != null && sample.kind() == SampleKind.PERFECT, sample);
		check("perfect jump headline", sample != null && sample.headline().equals("PERFECT +20 ms"), sample == null ? null : sample.headline());
	}

	private static void earlyJump() {
		TimingEngine engine = new TimingEngine();
		engine.onJump(0);
		engine.onHit(true, ms(90));
		engine.tick(ms(TimingEngine.PAIR_WINDOW_MS + 1));

		TimingSample sample = single(engine, TimingMode.JUMP);
		check("early jump delta", sample != null && near(sample.deltaMs(), -90.0), sample);
		check("early jump kind", sample != null && sample.kind() == SampleKind.EARLY, sample);
	}

	private static void noHitNoSample() {
		TimingEngine engine = new TimingEngine();
		engine.onJump(0);
		engine.tick(ms(5000));
		check("no hit means no sample", engine.stats(TimingMode.JUMP).total() == 0, engine.stats(TimingMode.JUMP).total());
	}

	private static void dealtHitDoesNotCountForJump() {
		TimingEngine engine = new TimingEngine();
		engine.onHit(false, 0);
		engine.onJump(ms(20));
		engine.tick(ms(20 + TimingEngine.PAIR_WINDOW_MS + 1));
		check("jump ignores hit dealt", engine.stats(TimingMode.JUMP).total() == 0, engine.stats(TimingMode.JUMP).total());
	}

	private static void dealtHitCountsForWTap() {
		TimingEngine engine = new TimingEngine();
		engine.onHit(false, 0);
		engine.onAction(TimingMode.WTAP, ms(30), 45.0, true, ms(90));
		engine.tick(ms(90 + TimingEngine.PAIR_WINDOW_MS));

		TimingSample sample = single(engine, TimingMode.WTAP);
		check("w tap after dealt hit", sample != null && near(sample.deltaMs(), 30.0), sample);
		check("w tap reference is dealt", sample != null && !sample.referenceTaken(), sample);
		check("w tap hold stored", sample != null && sample.hasHold() && near(sample.holdMs(), 45.0), sample);
	}

	private static void outsidePairWindow() {
		TimingEngine engine = new TimingEngine();
		engine.onHit(true, 0);
		engine.onJump(ms(400));
		engine.tick(ms(400 + TimingEngine.PAIR_WINDOW_MS + 1));
		check("action too far after hit", engine.stats(TimingMode.JUMP).total() == 0, engine.stats(TimingMode.JUMP).total());

		TimingEngine other = new TimingEngine();
		other.onJump(0);
		other.onHit(true, ms(400));
		other.tick(ms(400 + TimingEngine.PAIR_WINDOW_MS + 1));
		check("action too far before hit", other.stats(TimingMode.JUMP).total() == 0, other.stats(TimingMode.JUMP).total());
	}

	private static void hitUsedOncePerMode() {
		TimingEngine engine = new TimingEngine();
		engine.onHit(true, 0);
		engine.onJump(ms(10));
		engine.onAction(TimingMode.WTAP, ms(30), 40.0, true, ms(50));
		engine.tick(ms(TimingEngine.PAIR_WINDOW_MS + 60));

		check("jump sample recorded", engine.stats(TimingMode.JUMP).total() == 1, engine.stats(TimingMode.JUMP).total());
		check("w tap sample recorded", engine.stats(TimingMode.WTAP).total() == 1, engine.stats(TimingMode.WTAP).total());
	}

	private static void oneHitOnlyOneSamplePerMode() {
		TimingEngine engine = new TimingEngine();
		engine.onHit(true, 0);
		engine.onJump(ms(20));
		engine.onJump(ms(40));
		engine.tick(ms(40 + TimingEngine.PAIR_WINDOW_MS + 1));

		check("one hit feeds one jump", engine.stats(TimingMode.JUMP).total() == 1, engine.stats(TimingMode.JUMP).total());
	}

	private static void perfectWindowBoundary() {
		TimingEngine engine = new TimingEngine();
		engine.setPerfectWindowMs(50.0);
		engine.onHit(true, 0);
		engine.onJump(ms(50));
		engine.tick(ms(50 + TimingEngine.PAIR_WINDOW_MS + 1));
		TimingSample sample = single(engine, TimingMode.JUMP);
		check("exactly on the window edge is perfect", sample != null && sample.kind() == SampleKind.PERFECT, sample);

		TimingEngine late = new TimingEngine();
		late.setPerfectWindowMs(50.0);
		late.onHit(true, 0);
		late.onJump(ms(51));
		late.tick(ms(51 + TimingEngine.PAIR_WINDOW_MS + 1));
		TimingSample lateSample = single(late, TimingMode.JUMP);
		check("one ms outside is late", lateSample != null && lateSample.kind() == SampleKind.LATE, lateSample);
	}

	private static void nearestHitWins() {
		TimingEngine engine = new TimingEngine();
		engine.onHit(true, 0);
		engine.onHit(true, ms(120));
		engine.onJump(ms(100));
		engine.tick(ms(100 + TimingEngine.PAIR_WINDOW_MS + 1));

		TimingSample sample = single(engine, TimingMode.JUMP);
		check("nearest hit is used", sample != null && near(sample.deltaMs(), -20.0), sample);
	}

	private static void statsAggregation() {
		TimingEngine engine = new TimingEngine();
		engine.onHit(true, 0);
		engine.onJump(ms(10));
		engine.tick(ms(10 + TimingEngine.PAIR_WINDOW_MS + 1));

		engine.onHit(true, ms(2000));
		engine.onJump(ms(2080));
		engine.tick(ms(2080 + TimingEngine.PAIR_WINDOW_MS + 1));

		ModeStats stats = engine.stats(TimingMode.JUMP);
		check("two samples", stats.total() == 2, stats.total());
		check("one perfect", stats.perfectCount() == 1, stats.perfectCount());
		check("perfect rate 50%", near(stats.perfectRate(), 50.0), stats.perfectRate());
		check("average error 40 ms", near(stats.averageAbsError(), 40.0), stats.averageAbsError());
		check("recent is ordered", stats.recent().size() == 2 && near(stats.recent().get(1).deltaMs(), 80.0), stats.recent());
		check("total samples", engine.totalSamples() == 2, engine.totalSamples());
	}

	private static void tapDetection() {
		TapDetector detector = new TapDetector();
		detector.onWRelease(0);
		TapDetector.Tap wTap = detector.onWPress(ms(90));
		check("w tap detected", wTap != null && near(wTap.holdMs(), 90.0) && wTap.startNanos() == 0, wTap);

		check("press without release is not a tap", detector.onWPress(ms(1000)) == null, "unexpected tap");

		TapDetector slow = new TapDetector();
		slow.onWRelease(0);
		check("slow W re-press is not a tap", slow.onWPress(ms(600)) == null, "unexpected tap");

		TapDetector sDetector = new TapDetector();
		sDetector.onSPress(0);
		TapDetector.Tap sTap = sDetector.onSRelease(ms(80));
		check("s tap detected", sTap != null && near(sTap.holdMs(), 80.0), sTap);

		TapDetector held = new TapDetector();
		held.onSPress(0);
		held.tick(ms(600));
		check("held S is not a tap", held.onSRelease(ms(700)) == null, "unexpected tap");
	}

	private static void resetClearsEverything() {
		TimingEngine engine = new TimingEngine();
		engine.onHit(true, 0);
		engine.onJump(ms(10));
		engine.tick(ms(10 + TimingEngine.PAIR_WINDOW_MS + 1));
		check("sample before reset", engine.stats(TimingMode.JUMP).total() == 1, engine.stats(TimingMode.JUMP).total());

		engine.reset();
		check("stats cleared", engine.stats(TimingMode.JUMP).total() == 0 && engine.totalSamples() == 0, engine.totalSamples());
		check("last hit cleared", engine.sinceLastTakenMs(ms(100)) == Double.MAX_VALUE, engine.sinceLastTakenMs(ms(100)));
	}

	// ------------------------------------------------------------------

	private static TimingSample single(TimingEngine engine, TimingMode mode) {
		ModeStats stats = engine.stats(mode);

		if (stats.total() != 1) {
			return null;
		}

		return stats.last();
	}

	private static long ms(double value) {
		return (long) (value * 1_000_000.0);
	}

	private static boolean near(double a, double b) {
		return Math.abs(a - b) < 0.001;
	}

	private static void check(String name, boolean condition, Object detail) {
		checks++;

		if (condition) {
			System.out.println("  ok   " + name);
		} else {
			failures++;
			System.out.println("  FAIL " + name + "  -> " + detail);
		}
	}
}
