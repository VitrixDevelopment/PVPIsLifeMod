package net.vitrix.pvpislifemod.timing;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Iterator;

/**
 * Pure-Java timing engine. It knows nothing about Minecraft so it can be unit tested on its own.
 *
 * <p>Two kinds of reference events are recorded: hits you <b>take</b> and hits you <b>deal</b>.
 * A reset action (jump press, W tap start, S tap start) is paired with the closest reference hit
 * within {@link #PAIR_WINDOW_MS} milliseconds. Pairing is deliberately delayed until the pairing
 * window has fully elapsed, so an action that happens slightly <i>before</i> a hit can still be
 * matched to it (that is what makes "early" measurable at all &mdash; when the key is pressed the
 * future hit is not known yet).</p>
 *
 * <p>Sign convention: {@code delta = action - hit}. A negative delta means the reset happened
 * before the hit (early), a positive delta means it happened after the hit (late).</p>
 */
public final class TimingEngine {
	/** Maximum distance in milliseconds between a hit and a reset action for them to be paired. */
	public static final long PAIR_WINDOW_MS = 250L;
	public static final double NANOS_PER_MS = 1_000_000.0;

	private static final long PAIR_WINDOW_NANOS = (long) (PAIR_WINDOW_MS * NANOS_PER_MS);
	private static final long HIT_TTL_NANOS = PAIR_WINDOW_NANOS * 3L;

	private final EnumMap<TimingMode, ModeStats> stats = new EnumMap<>(TimingMode.class);
	private final Deque<Hit> hits = new ArrayDeque<>();
	private final Deque<Pending> pending = new ArrayDeque<>();

	private double perfectWindowMs = 50.0;
	private TimingMode lastMode = TimingMode.JUMP;
	private long lastTakenNanos = Long.MIN_VALUE;
	private long lastDealtNanos = Long.MIN_VALUE;

	public TimingEngine() {
		for (TimingMode mode : TimingMode.values()) {
			this.stats.put(mode, new ModeStats());
		}
	}

	// ------------------------------------------------------------------
	// configuration
	// ------------------------------------------------------------------

	public double perfectWindowMs() {
		return this.perfectWindowMs;
	}

	public void setPerfectWindowMs(double perfectWindowMs) {
		this.perfectWindowMs = Math.max(0.0, perfectWindowMs);
	}

	// ------------------------------------------------------------------
	// input events
	// ------------------------------------------------------------------

	/** Records a hit from the point of view of the local player. */
	public void onHit(boolean taken, long nowNanos) {
		this.hits.addLast(new Hit(nowNanos, taken));

		if (taken) {
			this.lastTakenNanos = nowNanos;
		} else {
			this.lastDealtNanos = nowNanos;
		}

		trim(nowNanos);
	}

	/** Convenience wrapper for a jump press. */
	public void onJump(long nowNanos) {
		this.onAction(TimingMode.JUMP, nowNanos, 0.0, false, nowNanos);
	}

	/**
	 * Queues a reset action for pairing.
	 *
	 * @param mode        technique that was performed
	 * @param actionNanos when the action started (for a W tap that is the moment W was released)
	 * @param holdMs      how long the tap was held in milliseconds
	 * @param hasHold     whether {@code holdMs} applies
	 * @param nowNanos    current time
	 */
	public void onAction(TimingMode mode, long actionNanos, double holdMs, boolean hasHold, long nowNanos) {
		if (mode == null) {
			return;
		}

		this.pending.addLast(new Pending(mode, actionNanos, actionNanos + PAIR_WINDOW_NANOS, holdMs, hasHold));
		resolve(nowNanos);
		trim(nowNanos);
	}

	/** Advances time: resolves finished pairings and drops stale data. Call once per client tick. */
	public void tick(long nowNanos) {
		resolve(nowNanos);
		trim(nowNanos);
	}

	// ------------------------------------------------------------------
	// pairing
	// ------------------------------------------------------------------

	private void resolve(long nowNanos) {
		if (this.pending.isEmpty()) {
			return;
		}

		for (Iterator<Pending> iterator = this.pending.iterator(); iterator.hasNext(); ) {
			Pending action = iterator.next();

			if (nowNanos < action.deadlineNanos) {
				// The pairing window is still open, a later hit could be the closest one.
				continue;
			}

			iterator.remove();

			Hit best = null;
			long bestDistance = Long.MAX_VALUE;

			for (Hit hit : this.hits) {
				if (hit.used.contains(action.mode) || !compatible(action.mode, hit)) {
					continue;
				}

				long distance = Math.abs(hit.nanos - action.actionNanos);

				if (distance <= PAIR_WINDOW_NANOS && distance < bestDistance) {
					bestDistance = distance;
					best = hit;
				}
			}

			if (best != null) {
				best.used.add(action.mode);
				record(action, best);
			}
		}
	}

	private static boolean compatible(TimingMode mode, Hit hit) {
		// A jump reset is always a reaction to being hit. Sprint resets happen both as a reaction
		// to being hit (defensive) and right after landing your own hit (offensive).
		return mode != TimingMode.JUMP || hit.taken;
	}

	private void record(Pending action, Hit hit) {
		double deltaMs = (action.actionNanos - hit.nanos) / NANOS_PER_MS;
		SampleKind kind;

		if (Math.abs(deltaMs) <= this.perfectWindowMs) {
			kind = SampleKind.PERFECT;
		} else if (deltaMs < 0) {
			kind = SampleKind.EARLY;
		} else {
			kind = SampleKind.LATE;
		}

		TimingSample sample = new TimingSample(
				action.mode,
				action.actionNanos,
				deltaMs,
				kind,
				hit.taken,
				action.holdMs,
				action.hasHold
		);

		this.stats.get(action.mode).add(sample);
		this.lastMode = action.mode;
	}

	private void trim(long nowNanos) {
		while (!this.hits.isEmpty() && nowNanos - this.hits.peekFirst().nanos > HIT_TTL_NANOS) {
			this.hits.removeFirst();
		}
	}

	// ------------------------------------------------------------------
	// queries
	// ------------------------------------------------------------------

	public ModeStats stats(TimingMode mode) {
		return this.stats.get(mode);
	}

	public TimingMode lastMode() {
		return this.lastMode;
	}

	/** Milliseconds since the last hit you took, or {@link Double#MAX_VALUE} if there was none. */
	public double sinceLastTakenMs(long nowNanos) {
		return this.lastTakenNanos == Long.MIN_VALUE
				? Double.MAX_VALUE
				: (nowNanos - this.lastTakenNanos) / NANOS_PER_MS;
	}

	/** Milliseconds since the last hit you dealt, or {@link Double#MAX_VALUE} if there was none. */
	public double sinceLastDealtMs(long nowNanos) {
		return this.lastDealtNanos == Long.MIN_VALUE
				? Double.MAX_VALUE
				: (nowNanos - this.lastDealtNanos) / NANOS_PER_MS;
	}

	public int totalSamples() {
		int total = 0;

		for (ModeStats modeStats : this.stats.values()) {
			total += modeStats.total();
		}

		return total;
	}

	public void reset() {
		this.hits.clear();
		this.pending.clear();
		this.lastTakenNanos = Long.MIN_VALUE;
		this.lastDealtNanos = Long.MIN_VALUE;
		this.lastMode = TimingMode.JUMP;

		for (ModeStats modeStats : this.stats.values()) {
			modeStats.reset();
		}
	}

	// ------------------------------------------------------------------
	// internal records
	// ------------------------------------------------------------------

	private static final class Hit {
		private final long nanos;
		private final boolean taken;
		private final EnumSet<TimingMode> used = EnumSet.noneOf(TimingMode.class);

		private Hit(long nanos, boolean taken) {
			this.nanos = nanos;
			this.taken = taken;
		}
	}

	private static final class Pending {
		private final TimingMode mode;
		private final long actionNanos;
		private final long deadlineNanos;
		private final double holdMs;
		private final boolean hasHold;

		private Pending(TimingMode mode, long actionNanos, long deadlineNanos, double holdMs, boolean hasHold) {
			this.mode = mode;
			this.actionNanos = actionNanos;
			this.deadlineNanos = deadlineNanos;
			this.holdMs = holdMs;
			this.hasHold = hasHold;
		}
	}
}
