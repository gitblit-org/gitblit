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

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 * Constant values used by Gitblit.
 *
 * @author James Moger
 *
 */
public class Constants {

	public static final String NAME = "Gitblit";

	public static final String FULL_NAME = "Gitblit - a pure Java Git solution";

	@Deprecated
	public static final String ADMIN_ROLE = "#admin";

	@Deprecated
	public static final String FORK_ROLE = "#fork";

	@Deprecated
	public static final String CREATE_ROLE = "#create";

	@Deprecated
	public static final String NOT_FEDERATED_ROLE = "#notfederated";

	@Deprecated
	public static final String NO_ROLE = "#none";

	public static final String EXTERNAL_ACCOUNT = "#externalAccount";

	public static final String PROPERTIES_FILE = "gitblit.properties";

	public static final String DEFAULT_USER_REPOSITORY_PREFIX = "~";

	public static final String R_PATH = "/r/";

	public static final String GIT_PATH = "/git/";
	
	public static final String REGEX_SHA256 = "[a-fA-F0-9]{64}";

	public static final String ZIP_PATH = "/zip/";

	public static final String SYNDICATION_PATH = "/feed/";

	public static final String FEDERATION_PATH = "/federation/";

	public static final String RPC_PATH = "/rpc/";

	public static final String PAGES = "/pages/";

	public static final String SPARKLESHARE_INVITE_PATH = "/sparkleshare/";

	public static final String RAW_PATH = "/raw/";

	public static final String PT_PATH = "/pt";

	public static final String BRANCH_GRAPH_PATH = "/graph/";

	public static final String BORDER = "*****************************************************************";

	public static final String BORDER2 = "#################################################################";

	public static final String FEDERATION_USER = "$gitblit";

	public static final String PROPOSAL_EXT = ".json";

	public static final String ENCODING = "UTF-8";

	public static final int LEN_SHORTLOG = 78;

	public static final int LEN_SHORTLOG_REFS = 60;

	public static final String DEFAULT_BRANCH = "default";

	public static final String CONFIG_GITBLIT = "gitblit";

	public static final String CONFIG_CUSTOM_FIELDS = "customFields";

	public static final String ISO8601 = "yyyy-MM-dd'T'HH:mm:ssZ";

	public static final String baseFolder = "baseFolder";

	public static final String baseFolder$ = "${" + baseFolder + "}";

	public static final String contextFolder$ = "${contextFolder}";

	public static final String HEAD = "HEAD";

	public static final String R_META = "refs/meta/";

	public static final String R_HEADS = "refs/heads/";

	public static final String R_NOTES = "refs/notes/";

	public static final String R_CHANGES = "refs/changes/";

	public static final String R_PULL = "refs/pull/";

	public static final String R_TAGS = "refs/tags/";

	public static final String R_REMOTES = "refs/remotes/";

	public static final String R_FOR = "refs/for/";

	public static final String R_TICKET = "refs/heads/ticket/";

	public static final String R_TICKETS_PATCHSETS = "refs/tickets/";

	public static final String R_MASTER = "refs/heads/master";

	public static final String MASTER = "master";

	public static final String R_DEVELOP = "refs/heads/develop";

	public static final String DEVELOP = "develop";

	public static final String ATTRIB_AUTHTYPE = NAME + ":authentication-type";

	public static final String ATTRIB_AUTHUSER = NAME + ":authenticated-user";
	
	public static final String R_LFS = "info/lfs/";

	public static String getVersion() {
		String v = Constants.class.getPackage().getImplementationVersion();
		if (v == null) {
			return "0.0.0-SNAPSHOT";
		}
		return v;
	}

	public static String getGitBlitVersion() {
		return NAME + " v" + getVersion();
	}

	public static String getBuildDate() {
		return getManifestValue("build-date", "PENDING");
	}

	public static String getASCIIArt() {
		StringBuilder sb = new StringBuilder();
		sb.append("  _____  _  _    _      _  _  _").append('\n');
		sb.append(" |  __ \\(_)| |  | |    | |(_)| |").append('\n');
		sb.append(" | |  \\/ _ | |_ | |__  | | _ | |_").append('\n');
		sb.append(" | | __ | || __|| '_ \\ | || || __|").append("  ").append("http://gitblit.com").append('\n');
		sb.append(" | |_\\ \\| || |_ | |_) || || || |_").append("   ").append("@gitblit").append('\n');
		sb.append("  \\____/|_| \\__||_.__/ |_||_| \\__|").append("  ").append(Constants.getVersion()).append('\n');
		return sb.toString();
	}

	private static String getManifestValue(String attrib, String defaultValue) {
		Class<?> clazz = Constants.class;
		String className = clazz.getSimpleName() + ".class";
		String classPath = clazz.getResource(className).toString();
		if (!classPath.startsWith("jar")) {
			// Class not from JAR
			return defaultValue;
		}
		try {
			String manifestPath = classPath.substring(0, classPath.lastIndexOf("!") + 1) + "/META-INF/MANIFEST.MF";
			Manifest manifest = new Manifest(new URL(manifestPath).openStream());
			Attributes attr = manifest.getMainAttributes();
			String value = attr.getValue(attrib);
			return value;
		} catch (Exception e) {
		}
		return defaultValue;
	}

	public static enum Role {
		NONE, ADMIN, CREATE, FORK, NOT_FEDERATED;

		public String getRole() {
			return "#" + name().replace("_", "").toLowerCase();
		}

		@Override
		public String toString() {
			return getRole();
		}
	}

	/**
	 * Enumeration representing the four access restriction levels.
	 */
	public static enum AccessRestrictionType {
		NONE, PUSH, CLONE, VIEW;

		private static final AccessRestrictionType [] AUTH_TYPES = { PUSH, CLONE, VIEW };

		public static AccessRestrictionType fromName(String name) {
			for (AccessRestrictionType type : values()) {
				if (type.name().equalsIgnoreCase(name)) {
					return type;
				}
			}
			return NONE;
		}

		public static List<AccessRestrictionType> choices(boolean allowAnonymousPush) {
			if (allowAnonymousPush) {
				return Arrays.asList(values());
			}
			return Arrays.asList(AUTH_TYPES);
		}

		public boolean exceeds(AccessRestrictionType type) {
			return this.ordinal() > type.ordinal();
		}

		public boolean atLeast(AccessRestrictionType type) {
			return this.ordinal() >= type.ordinal();
		}

		@Override
		public String toString() {
			return name();
		}

		public boolean isValidPermission(AccessPermission permission) {
			switch (this) {
			case VIEW:
				// VIEW restriction
				// all access permissions are valid
				return true;
			case CLONE:
				// CLONE restriction
				// only CLONE or greater access permissions are valid
				return permission.atLeast(AccessPermission.CLONE);
			case PUSH:
				// PUSH restriction
				// only PUSH or greater access permissions are valid
				return permission.atLeast(AccessPermission.PUSH);
			case NONE:
				// NO access restriction
				// all access permissions are invalid
				return false;
			}
			return false;
		}
	}

	/**
	 * Enumeration representing the types of authorization control for an
	 * access restricted resource.
	 */
	public static enum AuthorizationControl {
		AUTHENTICATED, NAMED;

		public static AuthorizationControl fromName(String name) {
			for (AuthorizationControl type : values()) {
				if (type.name().equalsIgnoreCase(name)) {
					return type;
				}
			}
			return NAMED;
		}

		@Override
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

		@Override
		public String toString() {
			return name();
		}
	}

	/**
	 * Enumeration representing the types of federation requests.
	 */
	public static enum FederationRequest {
		POKE, PROPOSAL, PULL_REPOSITORIES, PULL_USERS, PULL_TEAMS, PULL_SETTINGS, PULL_SCRIPTS, STATUS;

		public static FederationRequest fromName(String name) {
			for (FederationRequest type : values()) {
				if (type.name().equalsIgnoreCase(name)) {
					return type;
				}
			}
			return PULL_REPOSITORIES;
		}

		@Override
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
		// Order is important here.  anything after LIST_SETTINGS requires
		// administrator privileges and web.allowRpcManagement.
		CLEAR_REPOSITORY_CACHE, REINDEX_TICKETS, GET_PROTOCOL, LIST_REPOSITORIES, LIST_BRANCHES, GET_USER,
		FORK_REPOSITORY, LIST_SETTINGS,
		CREATE_REPOSITORY, EDIT_REPOSITORY, DELETE_REPOSITORY,
		LIST_USERS, CREATE_USER, EDIT_USER, DELETE_USER,
		LIST_TEAMS, CREATE_TEAM, EDIT_TEAM, DELETE_TEAM,
		LIST_REPOSITORY_MEMBERS, SET_REPOSITORY_MEMBERS, LIST_REPOSITORY_TEAMS, SET_REPOSITORY_TEAMS,
		LIST_REPOSITORY_MEMBER_PERMISSIONS, SET_REPOSITORY_MEMBER_PERMISSIONS, LIST_REPOSITORY_TEAM_PERMISSIONS, SET_REPOSITORY_TEAM_PERMISSIONS,
		LIST_FEDERATION_REGISTRATIONS, LIST_FEDERATION_RESULTS, LIST_FEDERATION_PROPOSALS, LIST_FEDERATION_SETS,
		EDIT_SETTINGS, LIST_STATUS;

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

	/**
	 * Enumeration of the search types.
	 */
	public static enum SearchType {
		AUTHOR, COMMITTER, COMMIT;

		public static SearchType forName(String name) {
			for (SearchType type : values()) {
				if (type.name().equalsIgnoreCase(name)) {
					return type;
				}
			}
			return COMMIT;
		}

		@Override
		public String toString() {
			return name().toLowerCase();
		}
	}

	/**
	 * Enumeration of the feed content object types.
	 */
	public static enum FeedObjectType {
		COMMIT, TAG;

		public static FeedObjectType forName(String name) {
			for (FeedObjectType type : values()) {
				if (type.name().equalsIgnoreCase(name)) {
					return type;
				}
			}
			return COMMIT;
		}

		@Override
		public String toString() {
			return name().toLowerCase();
		}
	}

	/**
	 * The types of objects that can be indexed and queried.
	 */
	public static enum SearchObjectType {
		commit, blob;

		public static SearchObjectType fromName(String name) {
			for (SearchObjectType value : values()) {
				if (value.name().equals(name)) {
					return value;
				}
			}
			return null;
		}
	}

	/**
	 * The access permissions available for a repository.
	 */
	public static enum AccessPermission {
		NONE("N"), EXCLUDE("X"), VIEW("V"), CLONE("R"), PUSH("RW"), CREATE("RWC"), DELETE("RWD"), REWIND("RW+"), OWNER("RW+");

		public static final AccessPermission [] NEWPERMISSIONS = { EXCLUDE, VIEW, CLONE, PUSH, CREATE, DELETE, REWIND };

		public static final AccessPermission [] SSHPERMISSIONS = { VIEW, CLONE, PUSH };

		public static AccessPermission LEGACY = REWIND;

		public final String code;

		private AccessPermission(String code) {
			this.code = code;
		}

		public boolean atMost(AccessPermission perm) {
			return ordinal() <= perm.ordinal();
		}

		public boolean atLeast(AccessPermission perm) {
			return ordinal() >= perm.ordinal();
		}

		public boolean exceeds(AccessPermission perm) {
			return ordinal() > perm.ordinal();
		}

		public String asRole(String repository) {
			return code + ":" + repository;
		}

		@Override
		public String toString() {
			return code;
		}

		public static AccessPermission permissionFromRole(String role) {
			String [] fields = role.split(":", 2);
			if (fields.length == 1) {
				// legacy/undefined assume full permissions
				return AccessPermission.LEGACY;
			} else {
				// code:repository
				return AccessPermission.fromCode(fields[0]);
			}
		}

		public static String repositoryFromRole(String role) {
			String [] fields = role.split(":", 2);
			if (fields.length == 1) {
				// legacy/undefined assume full permissions
				return role;
			} else {
				// code:repository
				return fields[1];
			}
		}

		public static AccessPermission fromCode(String code) {
			for (AccessPermission perm : values()) {
				if (perm.code.equalsIgnoreCase(code)) {
					return perm;
				}
			}
			return AccessPermission.NONE;
		}
	}

	public static enum RegistrantType {
		REPOSITORY, USER, TEAM;
	}

	public static enum PermissionType {
		MISSING, ANONYMOUS, EXPLICIT, TEAM, REGEX, OWNER, ADMINISTRATOR;
	}

	public static enum GCStatus {
		READY, COLLECTING;

		public boolean exceeds(GCStatus s) {
			return ordinal() > s.ordinal();
		}
	}

	public static enum AuthenticationType {
		PUBLIC_KEY, CREDENTIALS, COOKIE, CERTIFICATE, CONTAINER, HTTPHEADER;

		public boolean isStandard() {
			return ordinal() <= COOKIE.ordinal();
		}
	}

	public static enum AccountType {
		LOCAL, CONTAINER, LDAP, REDMINE, SALESFORCE, WINDOWS, PAM, HTPASSWD, HTTPHEADER;

		public static AccountType fromString(String value) {
			for (AccountType type : AccountType.values()) {
				if (type.name().equalsIgnoreCase(value)) {
					return type;
				}
			}
			return AccountType.LOCAL;
		}

		public boolean isLocal() {
			return this == LOCAL;
		}
	}

	public static enum CommitMessageRenderer {
		PLAIN, MARKDOWN;

		public static CommitMessageRenderer fromName(String name) {
			for (CommitMessageRenderer renderer : values()) {
				if (renderer.name().equalsIgnoreCase(name)) {
					return renderer;
				}
			}
			return CommitMessageRenderer.PLAIN;
		}
	}

	public static enum Transport {
		// ordered for url advertisements, assuming equal access permissions
		SSH, HTTPS, HTTP, GIT;

		public static Transport fromString(String value) {
			for (Transport t : values()) {
				if (t.name().equalsIgnoreCase(value)) {
					return t;
				}
			}
			return null;
		}

		public static Transport fromUrl(String url) {
			int delim = url.indexOf("://");
			if (delim == -1) {
				// if no protocol is specified, SSH is assumed by git clients
				return SSH;
			}
			String scheme = url.substring(0, delim);
			return fromString(scheme);
		}
	}

	@Documented
	@Retention(RetentionPolicy.RUNTIME)
	public @interface Unused {
	}
}
