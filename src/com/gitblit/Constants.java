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

	public static final String NAME = "Gitblit";

	public static final String FULL_NAME = "Gitblit - a pure Java Git solution";

	// The build script extracts this exact line so be careful editing it
	// and only use A-Z a-z 0-9 .-_ in the string.
	public static final String VERSION = "0.5.1";

	// The build script extracts this exact line so be careful editing it
	// and only use A-Z a-z 0-9 .-_ in the string.
	public static final String VERSION_DATE = "2011-06-28";

	// The build script extracts this exact line so be careful editing it
	// and only use A-Z a-z 0-9 .-_ in the string.
	public static final String JGIT_VERSION = "JGit 1.0.0 (201106090707-r)";

	public static final String ADMIN_ROLE = "#admin";

	public static final String PROPERTIES_FILE = "gitblit.properties";

	public static final String GIT_PATH = "/git/";

	public static final String ZIP_PATH = "/zip/";

	public static final String SYNDICATION_PATH = "/feed/";

	public static final String BORDER = "***********************************************************";

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
}
