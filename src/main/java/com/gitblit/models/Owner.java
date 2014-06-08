/*
 * Copyright 2014 gitblit.com.
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
import java.util.Set;
import java.util.TreeSet;

import com.gitblit.utils.StringUtils;

/**
 * The owner class defines the ownership method contract for an object.
 *
 * @author James Moger
 *
 */
public abstract class Owner implements Serializable {

	private static final long serialVersionUID = 1L;

	public final Set<String> ownedPaths = new TreeSet<String>();

	public abstract String getId();

	public abstract String getDisplayName();

	public abstract String getPersonalPath();

	public boolean isOwner(String path) {
		if (StringUtils.isEmpty(path)) {
			return false;
		}

		String personalPath = getPersonalPath();
		if (personalPath != null && path.startsWith(personalPath)) {
			return true;
		}

		if (ownedPaths == null) {
			return false;
		}

		if (ownedPaths.contains(path.toLowerCase())) {
			// exact path match
			return true;
		}

		for (String ownedPath : ownedPaths) {
			if (StringUtils.matchesIgnoreCase(path, ownedPath)) {
				// regex match
				return true;
			}
		}

		return false;
	}

	public void own(String path) {
		ownedPaths.add(path.toLowerCase());
	}

	public void disown(String path) {
		ownedPaths.remove(path.toLowerCase());
	}

	public boolean isOwner(RepositoryModel repository) {
		return isOwner(repository.name);
	}

	public void own(RepositoryModel repository) {
		own(repository.name);
	}

	public void disown(RepositoryModel repository) {
		disown(repository.name);
	}
}
