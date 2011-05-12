package com.gitblit;

public class Constants {

	public final static String NAME = "Git:Blit";

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
		
		public boolean atLeast(AccessRestrictionType type) {
			return this.ordinal() >= type.ordinal();
		}

		public String toString() {
			switch (this) {
			case NONE:
				return "Anonymous View, Clone, & Push";
			case PUSH:
				return "Anonymous View & Clone, Authenticated Push";
			case CLONE:
				return "Anonymous View, Authenticated Clone & Push";
			case VIEW:
				return "Authenticated View, Clone, & Push";
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
