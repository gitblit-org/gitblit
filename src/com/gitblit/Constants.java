package com.gitblit;

public class Constants {

	public final static String NAME = "Git:Blit";

	public final static String VERSION = "0.1.0-SNAPSHOT";

	public final static String ADMIN_ROLE = "admin";

	public final static String PULL_ROLE = "pull";

	public final static String PUSH_ROLE = "push";

	public final static String PROPERTIES_FILE = "gitblit.properties";

	public static String getGitBlitVersion() {
		return NAME + " v" + VERSION;
	}

	public static String getJGitVersion() {
		return "JGit 0.11.3";
	}

	public static String getRunningVersion() {
		return getGitBlitVersion();
	}
}
