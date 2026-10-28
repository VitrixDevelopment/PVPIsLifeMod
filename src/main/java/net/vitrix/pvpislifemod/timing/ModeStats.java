package net.vitrix.pvpislifemod.timing;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Running session statistics for a single {@link TimingMode}.
 */
public final class ModeStats {
	private static final int MAX_RECENT = 8;

	private final Deque<TimingSample> recent = new ArrayDeque<>();
	private TimingSample last;

	private int total;
	private int perfect;
	private int early;
	private int late;
	private double sumAbs;
	private double bestAbs = Double.MAX_VALUE;

	public void add(TimingSample sample) {
		this.last = sample;
		this.total++;
		this.sumAbs += Math.abs(sample.deltaMs());
		this.bestAbs = Math.min(this.bestAbs, Math.abs(sample.deltaMs()));

		switch (sample.kind()) {
			case PERFECT -> this.perfect++;
			case EARLY -> this.early++;
			case LATE -> this.late++;
		}

		this.recent.addLast(sample);

		while (this.recent.size() > MAX_RECENT) {
			this.recent.removeFirst();
		}
	}

	public void reset() {
		this.last = null;
		this.total = 0;
		this.perfect = 0;
		this.early = 0;
		this.late = 0;
		this.sumAbs = 0.0;
		this.bestAbs = Double.MAX_VALUE;
		this.recent.clear();
	}

	public TimingSample last() {
		return this.last;
	}

	public List<TimingSample> recent() {
		return new ArrayList<>(this.recent);
	}

	public int total() {
		return this.total;
	}

	public int perfectCount() {
		return this.perfect;
	}

	public int earlyCount() {
		return this.early;
	}

	public int lateCount() {
		return this.late;
	}

	/** Percentage of attempts inside the perfect window, 0-100. */
	public double perfectRate() {
		return this.total == 0 ? 0.0 : (this.perfect * 100.0) / this.total;
	}

	/** Average absolute error in milliseconds. */
	public double averageAbsError() {
		return this.total == 0 ? 0.0 : this.sumAbs / this.total;
	}

	/** Smallest absolute error in milliseconds, {@link Double#MAX_VALUE} when empty. */
	public double bestAbsError() {
		return this.bestAbs;
	}
}
