package com.gitblit;

public class Constants {

	public final static String NAME = "Git:Blit";

	// The build script extracts this exact line so be careful editing it
	// and only use A-Z a-z 0-9 .-_ in the string. 
	public final static String VERSION = "0.1.0-SNAPSHOT";

	public final static String ADMIN_ROLE = "#admin";

	public final static String PROPERTIES_FILE = "gitblit.properties";

	public static enum AccessRestrictionType {
		NONE, PUSH, CLONE, VIEW;

		public static AccessRestrictionType fromName(String name) {
			for (AccessRestrictionType type : values()) {
				if (type.name().equalsIgnoreCase(name)) {
					return type;
				}
			}
			return NONE;
		}

		public boolean exceeds(AccessRestrictionType type) {
			return this.ordinal() > type.ordinal();
		}

		public boolean atLeast(AccessRestrictionType type) {
			return this.ordinal() >= type.ordinal();
		}

		public String toString() {
			return name();
		}
	}

	public static String getGitBlitVersion() {
		return NAME + " v" + VERSION;
	}

	public static String getJGitVersion() {
		return "JGit 0.12.1";
	}

	public static String getRunningVersion() {
		return getGitBlitVersion();
	}
}
