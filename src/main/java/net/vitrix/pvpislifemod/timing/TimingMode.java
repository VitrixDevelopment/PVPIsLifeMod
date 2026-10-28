package net.vitrix.pvpislifemod.timing;

/**
 * The three sprint / knockback reset techniques that are timed.
 */
public enum TimingMode {
	/** Jump reset: react to a hit you take by jumping. */
	JUMP("Jump Reset", "JUMP RESET", 0xFF4FC3F7),
	/** W tap: release W and press it again to reset sprint. */
	WTAP("W Tap", "W TAP", 0xFFFFD54F),
	/** S tap: tap S to reset sprint. */
	STAP("S Tap", "S TAP", 0xFFBA68C8);

	private final String displayName;
	private final String hudLabel;
	private final int color;

	TimingMode(String displayName, String hudLabel, int color) {
		this.displayName = displayName;
		this.hudLabel = hudLabel;
		this.color = color;
	}

	public String displayName() {
		return this.displayName;
	}

	public String hudLabel() {
		return this.hudLabel;
	}

	/** ARGB colour used for this mode in the HUD. */
	public int color() {
		return this.color;
	}
}
