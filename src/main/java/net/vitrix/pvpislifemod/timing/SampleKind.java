package net.vitrix.pvpislifemod.timing;

/**
 * How a single reset attempt compares to the hit it reacted to.
 */
public enum SampleKind {
	/** Within the configured perfect window. */
	PERFECT("PERFECT", 0xFF55FF55),
	/** The reset happened before the hit arrived. */
	EARLY("EARLY", 0xFFFFC107),
	/** The reset happened after the hit arrived. */
	LATE("LATE", 0xFFFF5252);

	private final String label;
	private final int color;

	SampleKind(String label, int color) {
		this.label = label;
		this.color = color;
	}

	public String label() {
		return this.label;
	}

	public int color() {
		return this.color;
	}
}
