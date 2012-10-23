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
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.gitblit.Constants.AccessPermission;
import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.Constants.AuthorizationControl;
import com.gitblit.Constants.RegistrantType;
import com.gitblit.Constants.Unused;
import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.StringUtils;

/**
 * UserModel is a serializable model class that represents a user and the user's
 * restricted repository memberships. Instances of UserModels are also used as
 * servlet user principals.
 * 
 * @author James Moger
 * 
 */
public class UserModel implements Principal, Serializable, Comparable<UserModel> {

	private static final long serialVersionUID = 1L;

	public static final UserModel ANONYMOUS = new UserModel();
	
	// field names are reflectively mapped in EditUser page
	public String username;
	public String password;
	public String cookie;
	public String displayName;
	public String emailAddress;
	public boolean canAdmin;
	public boolean canFork;
	public boolean canCreate;
	public boolean excludeFromFederation;
	// retained for backwards-compatibility with RPC clients
	@Deprecated
	public final Set<String> repositories = new HashSet<String>();
	public final Map<String, AccessPermission> permissions = new LinkedHashMap<String, AccessPermission>();
	public final Set<TeamModel> teams = new HashSet<TeamModel>();

	// non-persisted fields
	public boolean isAuthenticated;
	
	public UserModel(String username) {
		this.username = username;
		this.isAuthenticated = true;
	}

	private UserModel() {
		this.username = "$anonymous";
		this.isAuthenticated = false;
	}

	/**
	 * This method does not take into consideration Ownership where the
	 * administrator has not explicitly granted access to the owner.
	 * 
	 * @param repositoryName
	 * @return
	 */
	@Deprecated
	public boolean canAccessRepository(String repositoryName) {
		return canAdmin() || repositories.contains(repositoryName.toLowerCase())
				|| hasTeamAccess(repositoryName);
	}

	@Deprecated
	@Unused
	public boolean canAccessRepository(RepositoryModel repository) {
		boolean isOwner = !StringUtils.isEmpty(repository.owner)
				&& repository.owner.equals(username);
		boolean allowAuthenticated = isAuthenticated && AuthorizationControl.AUTHENTICATED.equals(repository.authorizationControl);
		return canAdmin() || isOwner || repositories.contains(repository.name.toLowerCase())
				|| hasTeamAccess(repository.name) || allowAuthenticated;
	}

	@Deprecated
	@Unused
	public boolean hasTeamAccess(String repositoryName) {
		for (TeamModel team : teams) {
			if (team.hasRepositoryPermission(repositoryName)) {
				return true;
			}
		}
		return false;
	}
	
	@Deprecated
	@Unused
	public boolean hasRepository(String name) {
		return hasRepositoryPermission(name);
	}

	@Deprecated
	@Unused
	public void addRepository(String name) {
		addRepositoryPermission(name);
	}

	@Deprecated
	@Unused
	public void removeRepository(String name) {
		removeRepositoryPermission(name);
	}
	
	/**
	 * Returns a list of repository permissions for this user exclusive of
	 * permissions inherited from team memberships.
	 * 
	 * @return the user's list of permissions
	 */
	public List<RegistrantAccessPermission> getRepositoryPermissions() {
		List<RegistrantAccessPermission> list = new ArrayList<RegistrantAccessPermission>();
		for (Map.Entry<String, AccessPermission> entry : permissions.entrySet()) {
			list.add(new RegistrantAccessPermission(entry.getKey(), entry.getValue(), true, RegistrantType.REPOSITORY));
		}
		Collections.sort(list);
		return list;
	}
	
	/**
	 * Returns true if the user has any type of specified access permission for
	 * this repository.
	 * 
	 * @param name
	 * @return true if user has a specified access permission for the repository
	 */
	public boolean hasRepositoryPermission(String name) {
		String repository = AccessPermission.repositoryFromRole(name).toLowerCase();
		if (permissions.containsKey(repository)) {
			// exact repository permission specified
			return true;
		} else {
			// search for regex permission match
			for (String key : permissions.keySet()) {
				if (name.matches(key)) {
					AccessPermission p = permissions.get(key);
					if (p != null) {
						return true;
					}
				}
			}
		}
		return false;
	}
	
	/**
	 * Returns true if the user has an explicitly specified access permission for
	 * this repository.
	 * 
	 * @param name
	 * @return if the user has an explicitly specified access permission
	 */
	public boolean hasExplicitRepositoryPermission(String name) {
		String repository = AccessPermission.repositoryFromRole(name).toLowerCase();
		return permissions.containsKey(repository);
	}
	
	/**
	 * Adds a repository permission to the team.
	 * <p>
	 * Role may be formatted as:
	 * <ul>
	 * <li> myrepo.git <i>(this is implicitly RW+)</i>
	 * <li> RW+:myrepo.git
	 * </ul>
	 * @param role
	 */
	public void addRepositoryPermission(String role) {
		AccessPermission permission = AccessPermission.permissionFromRole(role);
		String repository = AccessPermission.repositoryFromRole(role).toLowerCase();
		repositories.add(repository);
		permissions.put(repository, permission);
	}
	
	public AccessPermission removeRepositoryPermission(String name) {
		String repository = AccessPermission.repositoryFromRole(name).toLowerCase();
		repositories.remove(repository);
		return permissions.remove(repository);
	}
		
	public void setRepositoryPermission(String repository, AccessPermission permission) {
		permissions.put(repository.toLowerCase(), permission);
	}

	public AccessPermission getRepositoryPermission(RepositoryModel repository) {
		if (canAdmin() || repository.isOwner(username) || repository.isUsersPersonalRepository(username)) {
			return AccessPermission.REWIND;
		}
		if (AuthorizationControl.AUTHENTICATED.equals(repository.authorizationControl) && isAuthenticated) {
			// AUTHENTICATED is a shortcut for authorizing all logged-in users RW access
			return AccessPermission.REWIND;
		}
		
		// explicit user permission OR user regex match is used
		// if that fails, then the best team permission is used
		AccessPermission permission = AccessPermission.NONE;
		if (permissions.containsKey(repository.name.toLowerCase())) {
			// exact repository permission specified, use it
			AccessPermission p = permissions.get(repository.name.toLowerCase());
			if (p != null) {
				return p;
			}
		} else {
			// search for case-insensitive regex permission match
			for (String key : permissions.keySet()) {
				if (StringUtils.matchesIgnoreCase(repository.name, key)) {
					AccessPermission p = permissions.get(key);
					if (p != null) {
						// take first match
						permission = p;
						break;
					}
				}
			}
		}
		
		if (AccessPermission.NONE.equals(permission)) {
			for (TeamModel team : teams) {
				AccessPermission p = team.getRepositoryPermission(repository);
				if (p.exceeds(permission)) {
					// use highest team permission
					permission = p;
				}
			}
		}
		return permission;
	}
	
	protected boolean canAccess(RepositoryModel repository, AccessRestrictionType ifRestriction, AccessPermission requirePermission) {
		if (repository.accessRestriction.atLeast(ifRestriction)) {
			AccessPermission permission = getRepositoryPermission(repository);
			return permission.atLeast(requirePermission);
		}
		return true;
	}
	
	public boolean canView(RepositoryModel repository) {
		return canAccess(repository, AccessRestrictionType.VIEW, AccessPermission.VIEW);
	}

	public boolean canClone(RepositoryModel repository) {
		return canAccess(repository, AccessRestrictionType.CLONE, AccessPermission.CLONE);
	}

	public boolean canPush(RepositoryModel repository) {
		if (repository.isFrozen) {
			return false;
		}
		return canAccess(repository, AccessRestrictionType.PUSH, AccessPermission.PUSH);
	}

	public boolean canCreateRef(RepositoryModel repository) {
		if (repository.isFrozen) {
			return false;
		}
		return canAccess(repository, AccessRestrictionType.PUSH, AccessPermission.CREATE);
	}

	public boolean canDeleteRef(RepositoryModel repository) {
		if (repository.isFrozen) {
			return false;
		}
		return canAccess(repository, AccessRestrictionType.PUSH, AccessPermission.DELETE);
	}

	public boolean canRewindRef(RepositoryModel repository) {
		if (repository.isFrozen) {
			return false;
		}
		return canAccess(repository, AccessRestrictionType.PUSH, AccessPermission.REWIND);
	}

	public boolean canFork(RepositoryModel repository) {
		if (repository.isUsersPersonalRepository(username)) {
			// can not fork your own repository
			return false;
		}
		if (canAdmin() || repository.isOwner(username)) {
			return true;
		}
		if (!repository.allowForks) {
			return false;
		}
		if (!isAuthenticated || !canFork()) {
			return false;
		}
		return canClone(repository);
	}
	
	public boolean canDelete(RepositoryModel model) {
		return canAdmin() || model.isUsersPersonalRepository(username);
	}
	
	public boolean canEdit(RepositoryModel model) {
		return canAdmin() || model.isUsersPersonalRepository(username) || model.isOwner(username);
	}
	
	/**
	 * This returns true if the user has fork privileges or the user has fork
	 * privileges because of a team membership.
	 * 
	 * @return true if the user can fork
	 */
	public boolean canFork() {
		if (canFork) {
			return true;
		}
		if (!ArrayUtils.isEmpty(teams)) {
			for (TeamModel team : teams) {
				if (team.canFork) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * This returns true if the user has admin privileges or the user has admin
	 * privileges because of a team membership.
	 * 
	 * @return true if the user can admin
	 */
	public boolean canAdmin() {
		if (canAdmin) {
			return true;
		}
		if (!ArrayUtils.isEmpty(teams)) {
			for (TeamModel team : teams) {
				if (team.canAdmin) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * This returns true if the user has create privileges or the user has create
	 * privileges because of a team membership.
	 * 
	 * @return true if the user can admin
	 */
	public boolean canCreate() {
		if (canCreate) {
			return true;
		}
		if (!ArrayUtils.isEmpty(teams)) {
			for (TeamModel team : teams) {
				if (team.canCreate) {
					return true;
				}
			}
		}
		return false;
	}
	
	/**
	 * Returns true if the user is allowed to create the specified repository.
	 * 
	 * @param repository
	 * @return true if the user can create the repository
	 */
	public boolean canCreate(String repository) {
		if (canAdmin()) {
			// admins can create any repository
			return true;
		}
		if (canCreate) {
			String projectPath = StringUtils.getFirstPathElement(repository);
			if (!StringUtils.isEmpty(projectPath) && projectPath.equalsIgnoreCase("~" + username)) {
				// personal repository
				return true;
			}
		}
		return false;
	}

	public boolean isTeamMember(String teamname) {
		for (TeamModel team : teams) {
			if (team.name.equalsIgnoreCase(teamname)) {
				return true;
			}
		}
		return false;
	}

	public TeamModel getTeam(String teamname) {
		if (teams == null) {
			return null;
		}
		for (TeamModel team : teams) {
			if (team.name.equalsIgnoreCase(teamname)) {
				return team;
			}
		}
		return null;
	}

	@Override
	public String getName() {
		return username;
	}
	
	public String getDisplayName() {
		if (StringUtils.isEmpty(displayName)) {
			return username;
		}
		return displayName;
	}
	
	public String getPersonalPath() {
		return "~" + username;
	}
	
	@Override
	public int hashCode() {
		return username.hashCode();
	}
	
	@Override
	public boolean equals(Object o) {
		if (o instanceof UserModel) {
			return username.equals(((UserModel) o).username);
		}
		return false;
	}

	@Override
	public String toString() {
		return username;
	}

	@Override
	public int compareTo(UserModel o) {
		return username.compareTo(o.username);
	}
	
	/**
	 * Returns true if the name/email pair match this user account.
	 * 
	 * @param name
	 * @param email
	 * @return true, if the name and email address match this account
	 */
	public boolean is(String name, String email) {
		// at a minimum a usename or display name must be supplied
		if (StringUtils.isEmpty(name)) {
			return false;
		}
		boolean nameVerified = name.equalsIgnoreCase(username) || name.equalsIgnoreCase(getDisplayName());
		boolean emailVerified = false;
		if (StringUtils.isEmpty(emailAddress)) {
			// user account has not specified an email address
			// rely on username/displayname verification
			emailVerified = true;
		} else {
			// user account has specified an email address
			// require email address verification
			if (!StringUtils.isEmpty(email)) {
				emailVerified = email.equalsIgnoreCase(emailAddress);
			}
		}
		return nameVerified && emailVerified;
	}
	
	public boolean hasBranchPermission(String repositoryName, String branch) {
		// Default UserModel doesn't implement branch-level security. Other Realms (i.e. Gerrit) may override this method.
		return hasRepositoryPermission(repositoryName);
	}
}
