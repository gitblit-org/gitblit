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
import java.util.ArrayList;
import java.util.List;

import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.StringUtils;

/**
 * A ForkModel represents a repository, its direct descendants, and its origin.
 *
 * @author James Moger
 *
 */
public class ForkModel implements Serializable {

	private static final long serialVersionUID = 1L;

	public final RepositoryModel repository;

	public final List<ForkModel> forks;

	public ForkModel(RepositoryModel repository) {
		this.repository = repository;
		this.forks = new ArrayList<ForkModel>();
	}

	public boolean isRoot() {
		return StringUtils.isEmpty(repository.originRepository);
	}

	public boolean isNode() {
		return !ArrayUtils.isEmpty(forks);
	}

	public boolean isLeaf() {
		return ArrayUtils.isEmpty(forks);
	}

	public boolean isPersonalRepository() {
		return repository.isPersonalRepository();
	}

	@Override
	public int hashCode() {
		return repository.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof ForkModel) {
			return repository.equals(((ForkModel) o).repository);
		}
		return false;
	}

	@Override
	public String toString() {
		return repository.toString();
	}
}
