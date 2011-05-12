package com.gitblit;

public class Constants {

	public final static String NAME = "Git:Blit";

	public final static String VERSION = "0.1.0-SNAPSHOT";

	public final static String ADMIN_ROLE = "#admin";

	public final static String PROPERTIES_FILE = "gitblit.properties";

	public static enum AccessRestrictionType {
		NONE, PUSH, CLONE, VIEW;

		public static AccessRestrictionType fromString(String name) {
			for (AccessRestrictionType type : values()) {
				if (type.toString().equalsIgnoreCase(name)) {
					return type;
				}
			}
			return NONE;
		}
		
		public boolean atLeast(AccessRestrictionType type) {
			return this.ordinal() >= type.ordinal();
		}

		public String toString() {
			switch (this) {
			case NONE:
				return "none";
			case PUSH:
				return "push";
			case CLONE:
				return "clone";
			case VIEW:
				return "view";
			}
			return "none";
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
