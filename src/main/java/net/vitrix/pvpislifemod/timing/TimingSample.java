package net.vitrix.pvpislifemod.timing;

import java.util.Locale;

/**
 * One timed reset attempt.
 *
 * @param mode           the technique that was timed
 * @param actionNanos    {@link System#nanoTime()} of the reset action (jump press, W tap start, S tap start)
 * @param deltaMs        milliseconds between the hit and the reset: negative = early, positive = late
 * @param kind           classification of {@code deltaMs}
 * @param referenceTaken {@code true} if the reference hit was a hit you took, {@code false} if it was a hit you dealt
 * @param holdMs         how long the tap was held (only meaningful for W / S taps)
 * @param hasHold        whether {@code holdMs} is meaningful
 */
public record TimingSample(
		TimingMode mode,
		long actionNanos,
		double deltaMs,
		SampleKind kind,
		boolean referenceTaken,
		double holdMs,
		boolean hasHold
) {
	/** Signed delta without the unit, e.g. {@code +37} or {@code -22}. */
	public String deltaText() {
		long rounded = Math.round(this.deltaMs);
		return (rounded >= 0 ? "+" : "") + rounded;
	}

	/** Signed delta with the unit, e.g. {@code +37 ms}. */
	public String deltaWithUnit() {
		return this.deltaText() + " ms";
	}

	/** Full headline, e.g. {@code LATE +37 ms}. */
	public String headline() {
		return this.kind.label() + " " + this.deltaWithUnit();
	}

	/** Absolute error, rounded to one decimal below 10 ms. */
	public String absoluteText() {
		double abs = Math.abs(this.deltaMs);

		if (abs < 10.0) {
			return String.format(Locale.ROOT, "%.1f ms", abs);
		}

		return Math.round(abs) + " ms";
	}

	/** Plain language version of the error, e.g. {@code late by 37 ms} or {@code early by 12.5 ms}. */
	public String describeError() {
		return switch (this.kind) {
			case PERFECT -> "on time";
			case EARLY -> "early by " + this.absoluteText();
			case LATE -> "late by " + this.absoluteText();
		};
	}

	/** Age of this sample in milliseconds. */
	public double ageMs(long nowNanos) {
		return (nowNanos - this.actionNanos) / TimingEngine.NANOS_PER_MS;
	}
}
