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

import com.gitblit.Constants.AccessPermission;
import com.gitblit.utils.StringUtils;

/**
 * Represents a Repository-AccessPermission tuple.
 * 
 * @author James Moger
 */
public class RepositoryAccessPermission implements Serializable, Comparable<RepositoryAccessPermission> {

	private static final long serialVersionUID = 1L;

	public String repository;
	public AccessPermission permission;

	public RepositoryAccessPermission() {
	}
	
	public RepositoryAccessPermission(String repository, AccessPermission permission) {
		this.repository = repository;
		this.permission = permission;
	}
	
	@Override
	public int compareTo(RepositoryAccessPermission p) {
		return StringUtils.compareRepositoryNames(repository, p.repository);
	}
	
	@Override
	public String toString() {
		return permission.asRole(repository);
	}
}