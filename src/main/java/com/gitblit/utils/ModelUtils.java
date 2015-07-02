/*
 * Copyright 2013 gitblit.com
 * Copyright 2013 Florian Zschocke
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
package com.gitblit.utils;

import com.gitblit.Constants;


/**
 * Utility functions for model classes that do not fit in any other category.
 *
 * @author Florian Zschocke
 */
public class ModelUtils
{
	private static String userRepoPrefix = Constants.DEFAULT_USER_REPOSITORY_PREFIX;

	/**
	 * Set the user repository prefix.
	 * @param prefix
	 */
	public static void setUserRepoPrefix(String prefix)
	{
		if (StringUtils.isEmpty(prefix)) {
			userRepoPrefix = Constants.DEFAULT_USER_REPOSITORY_PREFIX;
			return;
		}

		String newPrefix = prefix.replace('\\', '/');
		if (prefix.charAt(0) == '/') {
			newPrefix = prefix.substring(1);
		}

		userRepoPrefix = newPrefix;
	}


	/**
	 * Get the active user repository project prefix.
	 */
	public static String getUserRepoPrefix()
	{
		return userRepoPrefix;
	}


	/**
	 * Get the user project name for a user.
	 *
	 * @param username name of user
	 * @return the active user repository project prefix concatenated with the user name
	 */
	public static String getPersonalPath(String username)
	{
		return userRepoPrefix + username.toLowerCase();
	}


	/**
	 * Test if a repository path is for a personal repository.
	 *
	 * @param name
	 * 			A project name, a relative path to a repository.
	 * @return  true, if the name starts with the active user repository project prefix. False, otherwise.
	 */
	public static boolean isPersonalRepository(String name)
	{
		String testUserPrefix = userRepoPrefix;

		if (testUserPrefix.endsWith("/"))
			testUserPrefix = testUserPrefix.substring(0, testUserPrefix.length() - 1);

		if ( name.startsWith(testUserPrefix) ) return true;
		return false;
	}


	/**
	 * Test if a repository path is for a personal repository of a specific user.
	 *
	 * @param username
	 * 			Name of a user
	 * @param name
	 * 			A project name, a relative path to a repository.
	 * @return	true, if the name starts with the active user repository project prefix. False, otherwise.
	 */
	public static boolean isUsersPersonalRepository(String username, String name)
	{
		if ( name.equalsIgnoreCase(getPersonalPath(username)) ) return true;
		return false;
	}


	/**
	 * Exrtract a user's name from a personal repository path.
	 *
	 * @param path
	 * 			A project name, a relative path to a repository.
	 * @return  If the path does not point to a personal repository, an empty string is returned.
	 * 			Otherwise the name of the user the personal repository belongs to is returned.
	 */
	public static String getUserNameFromRepoPath(String path)
	{
		if ( !isPersonalRepository(path) ) return "";

		return path.substring(userRepoPrefix.length());
	}

}
