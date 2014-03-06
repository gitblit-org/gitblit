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
package com.gitblit.models;

import java.io.Serializable;
import java.util.List;

/**
 * Represents a Gravatar profile.
 *
 * @author James Moger
 *
 */
public class GravatarProfile implements Serializable {

	private static final long serialVersionUID = 1L;

	public String id;
	public String hash;
	public String requestHash;
	public String displayName;
	public String preferredUsername;
	public String currentLocation;
	public String aboutMe;

	public String profileUrl;
	public String thumbnailUrl;
	public List<ProfileObject> photos;
//	public Map<String, String> profileBackground;
//	public Map<String, String> name;

	public List<ProfileObject> phoneNumbers;
	public List<ProfileObject> emails;
	public List<ProfileObject> ims;
	public List<Account> accounts;
	public List<ProfileObject> urls;

	public static class ProfileObject implements Serializable {

		private static final long serialVersionUID = 1L;

		public String title;
		public String type;
		public String value;
		public boolean primary;

		@Override
		public String toString() {
			return value;
		}
	}

	public static class Account implements Serializable {

		private static final long serialVersionUID = 1L;

		public String domain;
		public String display;
		public String url;
		public String username;
		public String userid;
		public boolean verified;
		public String shortname;

		@Override
		public String toString() {
			return display;
		}
	}
}
