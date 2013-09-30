/*
 * Copyright 2012 gitblit.com.
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
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import com.gitblit.utils.ModelUtils;
import com.gitblit.utils.StringUtils;

/**
 * ProjectModel is a serializable model class.
 *
 * @author James Moger
 *
 */
public class ProjectModel implements Serializable, Comparable<ProjectModel> {

	private static final long serialVersionUID = 1L;

	// field names are reflectively mapped in EditProject page
	public final String name;
	public String title;
	public String description;
	public final Set<String> repositories = new HashSet<String>();

	public String projectMarkdown;
	public String repositoriesMarkdown;
	public Date lastChange;
	public final boolean isRoot;

	public ProjectModel(String name) {
		this(name, false);
	}

	public ProjectModel(String name, boolean isRoot) {
		this.name = name;
		this.isRoot = isRoot;
		this.lastChange = new Date(0);
		this.title = "";
		this.description = "";
	}

	public boolean isUserProject() {
		return ModelUtils.isPersonalRepository(name);
	}

	public boolean hasRepository(String name) {
		return repositories.contains(name.toLowerCase());
	}

	public void addRepository(String name) {
		repositories.add(name.toLowerCase());
	}

	public void addRepository(RepositoryModel model) {
		repositories.add(model.name.toLowerCase());
		if (lastChange.before(model.lastChange)) {
			lastChange = model.lastChange;
		}
	}

	public void addRepositories(Collection<String> names) {
		for (String name:names) {
			repositories.add(name.toLowerCase());
		}
	}

	public void removeRepository(String name) {
		repositories.remove(name.toLowerCase());
	}

	public String getDisplayName() {
		return StringUtils.isEmpty(title) ? name : title;
	}

	@Override
	public String toString() {
		return name;
	}

	@Override
	public int compareTo(ProjectModel o) {
		return name.compareTo(o.name);
	}
}
