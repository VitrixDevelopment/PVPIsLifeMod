package net.vitrix.pvpislifemod.timing;

/**
 * What the HUD box currently shows. {@link #AUTO} follows whichever technique was performed last.
 */
public enum HudMode {
	AUTO("AUTO"),
	JUMP("JUMP"),
	WTAP("W TAP"),
	STAP("S TAP");

	private final String label;

	HudMode(String label) {
		this.label = label;
	}

	public String label() {
		return this.label;
	}

	public HudMode next() {
		HudMode[] values = values();
		return values[(this.ordinal() + 1) % values.length];
	}

	/** The technique to lock the HUD to, or {@code null} for {@link #AUTO}. */
	public TimingMode timingMode() {
		return switch (this) {
			case JUMP -> TimingMode.JUMP;
			case WTAP -> TimingMode.WTAP;
			case STAP -> TimingMode.STAP;
			case AUTO -> null;
		};
	}
}
