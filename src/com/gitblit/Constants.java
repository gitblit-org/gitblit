package com.gitblit;

public class Constants {

	public final static String NAME = "Git:Blit";

	public final static String VERSION = "0.0.1";

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
