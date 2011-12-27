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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * TeamModel is a serializable model class that represents a group of users and
 * a list of accessible repositories.
 * 
 * @author James Moger
 * 
 */
public class TeamModel implements Serializable, Comparable<TeamModel> {

	private static final long serialVersionUID = 1L;

	// field names are reflectively mapped in EditTeam page
	public String name;
	public final Set<String> users = new HashSet<String>();
	public final Set<String> repositories = new HashSet<String>();
	public final Set<String> mailingLists = new HashSet<String>();
	public final List<String> preReceiveScripts = new ArrayList<String>();
	public final List<String> postReceiveScripts = new ArrayList<String>();

	public TeamModel(String name) {
		this.name = name;
	}

	public boolean hasRepository(String name) {
		return repositories.contains(name.toLowerCase());
	}

	public void addRepository(String name) {
		repositories.add(name.toLowerCase());
	}
	
	public void addRepositories(Collection<String> names) {
		for (String name:names) {
			repositories.add(name.toLowerCase());
		}
	}	

	public void removeRepository(String name) {
		repositories.remove(name.toLowerCase());
	}
	
	public boolean hasUser(String name) {
		return users.contains(name.toLowerCase());
	}

	public void addUser(String name) {
		users.add(name.toLowerCase());
	}

	public void addUsers(Collection<String> names) {
		for (String name:names) {
			users.add(name.toLowerCase());
		}
	}

	public void removeUser(String name) {
		users.remove(name.toLowerCase());
	}

	public void addMailingLists(Collection<String> addresses) {
		for (String address:addresses) {
			mailingLists.add(address.toLowerCase());
		}
	}

	@Override
	public String toString() {
		return name;
	}

	@Override
	public int compareTo(TeamModel o) {
		return name.compareTo(o.name);
	}
}
