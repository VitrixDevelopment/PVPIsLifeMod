package net.vitrix.pvpislifemod.timing;

/**
 * Turns raw key transitions into tap events. A tap is only reported once it is complete
 * (W: released then pressed again, S: pressed then released) so that simply stopping to walk
 * is never counted as a sprint reset.
 */
public final class TapDetector {
	/** A key that is held longer than this is not a tap any more. */
	public static final long MAX_TAP_MS = 500L;

	private static final long NONE = Long.MIN_VALUE;
	private static final long MAX_TAP_NANOS = MAX_TAP_MS * 1_000_000L;

	private long wReleaseNanos = NONE;
	private long sPressNanos = NONE;

	/** A completed tap: when it started and how long it was held. */
	public record Tap(long startNanos, double holdMs) {
	}

	/** Called when W is released; the tap only counts as such if W is pressed again soon. */
	public void onWRelease(long nowNanos) {
		this.wReleaseNanos = nowNanos;
	}

	/** Called when W is pressed. Returns the completed W tap, or {@code null} if this was not a tap. */
	public Tap onWPress(long nowNanos) {
		long start = this.wReleaseNanos;
		this.wReleaseNanos = NONE;

		if (start == NONE) {
			return null;
		}

		double holdMs = (nowNanos - start) / TimingEngine.NANOS_PER_MS;

		if (holdMs < 0.0 || holdMs > MAX_TAP_MS) {
			return null;
		}

		return new Tap(start, holdMs);
	}

	/** Called when S is pressed; the tap only counts as such if S is released again soon. */
	public void onSPress(long nowNanos) {
		this.sPressNanos = nowNanos;
	}

	/** Called when S is released. Returns the completed S tap, or {@code null} if this was not a tap. */
	public Tap onSRelease(long nowNanos) {
		long start = this.sPressNanos;
		this.sPressNanos = NONE;

		if (start == NONE) {
			return null;
		}

		double holdMs = (nowNanos - start) / TimingEngine.NANOS_PER_MS;

		if (holdMs < 0.0 || holdMs > MAX_TAP_MS) {
			return null;
		}

		return new Tap(start, holdMs);
	}

	public void tick(long nowNanos) {
		if (this.wReleaseNanos != NONE && nowNanos - this.wReleaseNanos > MAX_TAP_NANOS) {
			this.wReleaseNanos = NONE;
		}

		if (this.sPressNanos != NONE && nowNanos - this.sPressNanos > MAX_TAP_NANOS) {
			this.sPressNanos = NONE;
		}
	}

	public void reset() {
		this.wReleaseNanos = NONE;
		this.sPressNanos = NONE;
	}
}
