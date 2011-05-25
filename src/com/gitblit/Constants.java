/*
 * Copyright 2011 gitblit.com.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gitblit;

public class Constants {

	public final static String NAME = "Gitblit";
	
	public final static String FULL_NAME = "Gitblit - a pure Java Git solution";

	// The build script extracts this exact line so be careful editing it
	// and only use A-Z a-z 0-9 .-_ in the string. 
	public final static String VERSION = "0.1.0-SNAPSHOT";

	// The build script extracts this exact line so be careful editing it
	// and only use A-Z a-z 0-9 .-_ in the string.
	public final static String JGIT_VERSION = "JGit 0.12.1";

	public final static String ADMIN_ROLE = "#admin";

	public final static String PROPERTIES_FILE = "gitblit.properties";
	
	public final static String GIT_SERVLET_PATH = "/git/";
	
	public final static String ZIP_SERVLET_PATH = "/zip/";

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
		return JGIT_VERSION;
	}

	public static String getRunningVersion() {
		return getGitBlitVersion();
	}
}
