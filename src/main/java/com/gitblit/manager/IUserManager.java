/*
 * Copyright 2013 gitblit.com.
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
package com.gitblit.manager;

import java.util.List;

import com.gitblit.IUserService;
import com.gitblit.models.Owner;
import com.gitblit.models.RepositoryModel;

public interface IUserManager extends IManager, IUserService {

	/**
	 * Returns true if the username represents an internal account
	 *
	 * @param username
	 * @return true if the specified username represents an internal account
 	 * @since 1.4.0
	 */
	boolean isInternalAccount(String username);

	/**
	 * Returns the list of repository owners.
	 *
	 * @param repository
	 * @return a list of owners
	 * @since 1.6.0
	 */
	List<Owner> getOwners(RepositoryModel repository);

	/**
	 * Sets the repository owners.
	 *
	 * @param repository
	 * @param a list of owners
	 * @return true if successful
	 * @since 1.6.0
	 */
	boolean setOwners(RepositoryModel repository, List<Owner> owners);

//	/**
//	 * Returns the list of project owners.
//	 *
//	 * @param project
//	 * @return a list of owners
//	 * @since 1.6.0
//	 */
//	List<RepositoryOwner> getOwners(ProjectModel project);
//
}