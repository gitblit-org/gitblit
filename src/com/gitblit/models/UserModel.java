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
import java.util.HashSet;
import java.util.Set;

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

	// field names are reflectively mapped in EditUser page
	public String username;
	public String password;
	public boolean canAdmin;
	public boolean excludeFromFederation;
	public final Set<String> repositories = new HashSet<String>();

	public UserModel(String username) {
		this.username = username;
	}

	public boolean canAccessRepository(String repositoryName) {
		RepositoryModel repository = GitBlit.self().getRepositoryModel(repositoryName);
		return canAdmin || repositories.contains(repositoryName.toLowerCase()) || repository.owner.equalsIgnoreCase(username);
	}

	public void addRepository(String name) {
		repositories.add(name.toLowerCase());
	}

	@Override
	public String getName() {
		return username;
	}

	@Override
	public String toString() {
		return username;
	}

	@Override
	public int compareTo(UserModel o) {
		return username.compareTo(o.username);
	}
}
