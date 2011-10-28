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

/**
 * Constant values used by Gitblit.
 * 
 * @author James Moger
 * 
 */
public class Constants {

	public static final String NAME = "Gitblit";

	public static final String FULL_NAME = "Gitblit - a pure Java Git solution";

	// The build script extracts this exact line so be careful editing it
	// and only use A-Z a-z 0-9 .-_ in the string.
	public static final String VERSION = "0.7.0-SNAPSHOT";

	// The build script extracts this exact line so be careful editing it
	// and only use A-Z a-z 0-9 .-_ in the string.
	public static final String VERSION_DATE = "PENDING";

	// The build script extracts this exact line so be careful editing it
	// and only use A-Z a-z 0-9 .-_ in the string.
	public static final String JGIT_VERSION = "JGit 1.1.0 (201109151100-r)";

	public static final String ADMIN_ROLE = "#admin";

	public static final String NOT_FEDERATED_ROLE = "#notfederated";

	public static final String PROPERTIES_FILE = "gitblit.properties";

	public static final String GIT_PATH = "/git/";

	public static final String ZIP_PATH = "/zip/";

	public static final String SYNDICATION_PATH = "/feed/";

	public static final String FEDERATION_PATH = "/federation/";

	public static final String RPC_PATH = "/rpc/";

	public static final String BORDER = "***********************************************************";

	public static final String FEDERATION_USER = "$gitblit";

	public static final String PROPOSAL_EXT = ".json";

	public static String getGitBlitVersion() {
		return NAME + " v" + VERSION;
	}

	/**
	 * Enumeration representing the four access restriction levels.
	 */
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

	/**
	 * Enumeration representing the types of federation tokens.
	 */
	public static enum FederationToken {
		ALL, USERS_AND_REPOSITORIES, REPOSITORIES;

		public static FederationToken fromName(String name) {
			for (FederationToken type : values()) {
				if (type.name().equalsIgnoreCase(name)) {
					return type;
				}
			}
			return REPOSITORIES;
		}

		public String toString() {
			return name();
		}
	}

	/**
	 * Enumeration representing the types of federation requests.
	 */
	public static enum FederationRequest {
		POKE, PROPOSAL, PULL_REPOSITORIES, PULL_USERS, PULL_SETTINGS, STATUS;

		public static FederationRequest fromName(String name) {
			for (FederationRequest type : values()) {
				if (type.name().equalsIgnoreCase(name)) {
					return type;
				}
			}
			return PULL_REPOSITORIES;
		}

		public String toString() {
			return name();
		}
	}

	/**
	 * Enumeration representing the statii of federation requests.
	 */
	public static enum FederationPullStatus {
		PENDING, FAILED, SKIPPED, PULLED, MIRRORED, NOCHANGE, EXCLUDED;

		public static FederationPullStatus fromName(String name) {
			for (FederationPullStatus type : values()) {
				if (type.name().equalsIgnoreCase(name)) {
					return type;
				}
			}
			return PENDING;
		}

		@Override
		public String toString() {
			return name();
		}
	}

	/**
	 * Enumeration representing the federation types.
	 */
	public static enum FederationStrategy {
		EXCLUDE, FEDERATE_THIS, FEDERATE_ORIGIN;

		public static FederationStrategy fromName(String name) {
			for (FederationStrategy type : values()) {
				if (type.name().equalsIgnoreCase(name)) {
					return type;
				}
			}
			return FEDERATE_THIS;
		}

		public boolean exceeds(FederationStrategy type) {
			return this.ordinal() > type.ordinal();
		}

		public boolean atLeast(FederationStrategy type) {
			return this.ordinal() >= type.ordinal();
		}

		@Override
		public String toString() {
			return name();
		}
	}

	/**
	 * Enumeration representing the possible results of federation proposal
	 * requests.
	 */
	public static enum FederationProposalResult {
		ERROR, FEDERATION_DISABLED, MISSING_DATA, NO_PROPOSALS, NO_POKE, ACCEPTED;

		@Override
		public String toString() {
			return name();
		}
	}

	/**
	 * Enumeration representing the possible remote procedure call requests from
	 * a client.
	 */
	public static enum RpcRequest {
		LIST_REPOSITORIES, LIST_BRANCHES, CREATE_REPOSITORY, EDIT_REPOSITORY, DELETE_REPOSITORY,
		LIST_USERS, CREATE_USER, EDIT_USER, DELETE_USER, LIST_REPOSITORY_MEMBERS,
		SET_REPOSITORY_MEMBERS, LIST_FEDERATION_REGISTRATIONS, LIST_FEDERATION_RESULTS,
		LIST_FEDERATION_PROPOSALS, LIST_FEDERATION_SETS, LIST_SETTINGS, EDIT_SETTINGS,
		LIST_STATUS;

		public static RpcRequest fromName(String name) {
			for (RpcRequest type : values()) {
				if (type.name().equalsIgnoreCase(name)) {
					return type;
				}
			}
			return null;
		}

		public boolean exceeds(RpcRequest type) {
			return this.ordinal() > type.ordinal();
		}

		@Override
		public String toString() {
			return name();
		}
	}
}
