package com.gitblit.utils;

import com.gitblit.IStoredSettings;
import com.gitblit.Keys;

/**
 * Utility functions for model classes that do not fit in any other category.
 *
 * @author Florian Zschocke
 */
public class ModelUtils
{
	/**
	 * Default value for the prefix for user repository directories.
	 */
	private static final String DEFAULT_USER_REPO_PREFIX = "~";

	private static String userRepoPrefix = DEFAULT_USER_REPO_PREFIX;



	/**
	 * Set the user repository prefix from configuration settings.
	 * @param settings
	 */
	public static void setUserRepoPrefix(IStoredSettings settings)
	{
		String newPrefix = DEFAULT_USER_REPO_PREFIX;
		if (settings != null) {
			String prefix = settings.getString(Keys.git.userRepositoryPrefix, DEFAULT_USER_REPO_PREFIX);
			if (prefix != null && !prefix.trim().isEmpty()) {
				if (prefix.charAt(0) == '/') prefix = prefix.substring(1);
				newPrefix = prefix;
			}
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
		if ( name.startsWith(getUserRepoPrefix()) ) return true;
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

		return path.substring(getUserRepoPrefix().length());
	}

}
