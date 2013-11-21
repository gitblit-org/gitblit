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

import java.io.File;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.eclipse.jgit.lib.Repository;

import com.gitblit.GitBlitException;
import com.gitblit.models.ForkModel;
import com.gitblit.models.Metric;
import com.gitblit.models.RegistrantAccessPermission;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.SearchResult;
import com.gitblit.models.UserModel;

public interface IRepositoryManager extends IManager {

	/**
	 * Returns the path of the repositories folder. This method checks to see if
	 * Gitblit is running on a cloud service and may return an adjusted path.
	 *
	 * @return the repositories folder path
	 */
	File getRepositoriesFolder();

	/**
	 * Returns the path of the hooks folder. This method checks to see if
	 * Gitblit is running on a cloud service and may return an adjusted path.
	 *
	 * @return the Groovy hook scripts folder path
	 */
	File getHooksFolder();

	/**
	 * Returns the path of the grapes folder. This method checks to see if
	 * Gitblit is running on a cloud service and may return an adjusted path.
	 *
	 * @return the Groovy grapes folder path
	 */
	File getGrapesFolder();

	/**
	 * Returns the most recent change date of any repository served by Gitblit.
	 *
	 * @return a date
	 */
	Date getLastActivityDate();

	/**
	 * Returns the effective list of permissions for this user, taking into account
	 * team memberships, ownerships.
	 *
	 * @param user
	 * @return the effective list of permissions for the user
	 */
	List<RegistrantAccessPermission> getUserAccessPermissions(UserModel user);

	/**
	 * Returns the list of users and their access permissions for the specified
	 * repository including permission source information such as the team or
	 * regular expression which sets the permission.
	 *
	 * @param repository
	 * @return a list of RegistrantAccessPermissions
	 */
	List<RegistrantAccessPermission> getUserAccessPermissions(RepositoryModel repository);

	/**
	 * Sets the access permissions to the specified repository for the specified users.
	 *
	 * @param repository
	 * @param permissions
	 * @return true if the user models have been updated
	 */
	boolean setUserAccessPermissions(RepositoryModel repository, Collection<RegistrantAccessPermission> permissions);

	/**
	 * Returns the list of all users who have an explicit access permission
	 * for the specified repository.
	 *
	 * @see IUserService.getUsernamesForRepositoryRole(String)
	 * @param repository
	 * @return list of all usernames that have an access permission for the repository
	 */
	List<String> getRepositoryUsers(RepositoryModel repository);

	/**
	 * Returns the list of teams and their access permissions for the specified
	 * repository including the source of the permission such as the admin flag
	 * or a regular expression.
	 *
	 * @param repository
	 * @return a list of RegistrantAccessPermissions
	 */
	List<RegistrantAccessPermission> getTeamAccessPermissions(RepositoryModel repository);

	/**
	 * Sets the access permissions to the specified repository for the specified teams.
	 *
	 * @param repository
	 * @param permissions
	 * @return true if the team models have been updated
	 */
	boolean setTeamAccessPermissions(RepositoryModel repository, Collection<RegistrantAccessPermission> permissions);

	/**
	 * Returns the list of all teams who have an explicit access permission for
	 * the specified repository.
	 *
	 * @see IUserService.getTeamnamesForRepositoryRole(String)
	 * @param repository
	 * @return list of all teamnames with explicit access permissions to the repository
	 */
	List<String> getRepositoryTeams(RepositoryModel repository);

	/**
	 * Adds the repository to the list of cached repositories if Gitblit is
	 * configured to cache the repository list.
	 *
	 * @param model
	 */
	void addToCachedRepositoryList(RepositoryModel model);

	/**
	 * Resets the repository list cache.
	 *
	 */
	void resetRepositoryListCache();

	/**
	 * Returns the list of all repositories available to Gitblit. This method
	 * does not consider user access permissions.
	 *
	 * @return list of all repositories
	 */
	List<String> getRepositoryList();

	/**
	 * Returns the JGit repository for the specified name.
	 *
	 * @param repositoryName
	 * @return repository or null
	 */
	Repository getRepository(String repositoryName);

	/**
	 * Returns the JGit repository for the specified name.
	 *
	 * @param repositoryName
	 * @param logError
	 * @return repository or null
	 */
	Repository getRepository(String repositoryName, boolean logError);

	/**
	 * Returns the list of repository models that are accessible to the user.
	 *
	 * @param user
	 * @return list of repository models accessible to user
	 */
	List<RepositoryModel> getRepositoryModels(UserModel user);

	/**
	 * Returns a repository model if the repository exists and the user may
	 * access the repository.
	 *
	 * @param user
	 * @param repositoryName
	 * @return repository model or null
	 */
	RepositoryModel getRepositoryModel(UserModel user, String repositoryName);

	/**
	 * Returns the repository model for the specified repository. This method
	 * does not consider user access permissions.
	 *
	 * @param repositoryName
	 * @return repository model or null
	 */
	RepositoryModel getRepositoryModel(String repositoryName);

	/**
	 * Returns the star count of the repository.
	 *
	 * @param repository
	 * @return the star count
	 */
	long getStarCount(RepositoryModel repository);

	/**
	 * Determines if this server has the requested repository.
	 *
	 * @param n
	 * @return true if the repository exists
	 */
	boolean hasRepository(String repositoryName);

	/**
	 * Determines if this server has the requested repository.
	 *
	 * @param n
	 * @param caseInsensitive
	 * @return true if the repository exists
	 */
	boolean hasRepository(String repositoryName, boolean caseSensitiveCheck);

	/**
	 * Determines if the specified user has a fork of the specified origin
	 * repository.
	 *
	 * @param username
	 * @param origin
	 * @return true the if the user has a fork
	 */
	boolean hasFork(String username, String origin);

	/**
	 * Gets the name of a user's fork of the specified origin
	 * repository.
	 *
	 * @param username
	 * @param origin
	 * @return the name of the user's fork, null otherwise
	 */
	String getFork(String username, String origin);

	/**
	 * Returns the fork network for a repository by traversing up the fork graph
	 * to discover the root and then down through all children of the root node.
	 *
	 * @param repository
	 * @return a ForkModel
	 */
	ForkModel getForkNetwork(String repository);

	/**
	 * Updates the last changed fields and optionally calculates the size of the
	 * repository.  Gitblit caches the repository sizes to reduce the performance
	 * penalty of recursive calculation. The cache is updated if the repository
	 * has been changed since the last calculation.
	 *
	 * @param model
	 * @return size in bytes of the repository
	 */
	long updateLastChangeFields(Repository r, RepositoryModel model);

	/**
	 * Returns the metrics for the default branch of the specified repository.
	 * This method builds a metrics cache. The cache is updated if the
	 * repository is updated. A new copy of the metrics list is returned on each
	 * call so that modifications to the list are non-destructive.
	 *
	 * @param model
	 * @param repository
	 * @return a new array list of metrics
	 */
	List<Metric> getRepositoryDefaultMetrics(RepositoryModel model, Repository repository);

	/**
	 * Creates/updates the repository model keyed by reopsitoryName. Saves all
	 * repository settings in .git/config. This method allows for renaming
	 * repositories and will update user access permissions accordingly.
	 *
	 * All repositories created by this method are bare and automatically have
	 * .git appended to their names, which is the standard convention for bare
	 * repositories.
	 *
	 * @param repositoryName
	 * @param repository
	 * @param isCreate
	 * @throws GitBlitException
	 */
	void updateRepositoryModel(String repositoryName, RepositoryModel repository, boolean isCreate)
			throws GitBlitException;

	/**
	 * Updates the Gitblit configuration for the specified repository.
	 *
	 * @param r
	 *            the Git repository
	 * @param repository
	 *            the Gitblit repository model
	 */
	void updateConfiguration(Repository r, RepositoryModel repository);

	/**
	 * Deletes the repository from the file system and removes the repository
	 * permission from all repository users.
	 *
	 * @param model
	 * @return true if successful
	 */
	boolean deleteRepositoryModel(RepositoryModel model);

	/**
	 * Deletes the repository from the file system and removes the repository
	 * permission from all repository users.
	 *
	 * @param repositoryName
	 * @return true if successful
	 */
	boolean deleteRepository(String repositoryName);

	/**
	 * Returns the list of all Groovy push hook scripts. Script files must have
	 * .groovy extension
	 *
	 * @return list of available hook scripts
	 */
	List<String> getAllScripts();

	/**
	 * Returns the list of pre-receive scripts the repository inherited from the
	 * global settings and team affiliations.
	 *
	 * @param repository
	 *            if null only the globally specified scripts are returned
	 * @return a list of scripts
	 */
	List<String> getPreReceiveScriptsInherited(RepositoryModel repository);

	/**
	 * Returns the list of all available Groovy pre-receive push hook scripts
	 * that are not already inherited by the repository. Script files must have
	 * .groovy extension
	 *
	 * @param repository
	 *            optional parameter
	 * @return list of available hook scripts
	 */
	List<String> getPreReceiveScriptsUnused(RepositoryModel repository);

	/**
	 * Returns the list of post-receive scripts the repository inherited from
	 * the global settings and team affiliations.
	 *
	 * @param repository
	 *            if null only the globally specified scripts are returned
	 * @return a list of scripts
	 */
	List<String> getPostReceiveScriptsInherited(RepositoryModel repository);

	/**
	 * Returns the list of unused Groovy post-receive push hook scripts that are
	 * not already inherited by the repository. Script files must have .groovy
	 * extension
	 *
	 * @param repository
	 *            optional parameter
	 * @return list of available hook scripts
	 */
	List<String> getPostReceiveScriptsUnused(RepositoryModel repository);

	/**
	 * Search the specified repositories using the Lucene query.
	 *
	 * @param query
	 * @param page
	 * @param pageSize
	 * @param repositories
	 * @return
	 */
	List<SearchResult> search(String query, int page, int pageSize, List<String> repositories);

	/**
	 *
	 * @return true if we are running the gc executor
	 */
	boolean isCollectingGarbage();

	/**
	 * Returns true if Gitblit is actively collecting garbage in this repository.
	 *
	 * @param repositoryName
	 * @return true if actively collecting garbage
	 */
	boolean isCollectingGarbage(String repositoryName);

}