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
package com.gitblit;

import java.text.MessageFormat;

import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.Constants.AuthorizationControl;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.StringUtils;

/**
 * The GitFilter is an AccessRestrictionFilter which ensures that Git client
 * requests for push, clone, or view restricted repositories are authenticated
 * and authorized.
 * 
 * @author James Moger
 * 
 */
public class GitFilter extends AccessRestrictionFilter {

	protected static final String gitReceivePack = "/git-receive-pack";

	protected static final String gitUploadPack = "/git-upload-pack";

	protected static final String[] suffixes = { gitReceivePack, gitUploadPack, "/info/refs", "/HEAD",
			"/objects" };

	/**
	 * Extract the repository name from the url.
	 * 
	 * @param cloneUrl
	 * @return repository name
	 */
	public static String getRepositoryName(String value) {
		String repository = value;
		// get the repository name from the url by finding a known url suffix
		for (String urlSuffix : suffixes) {
			if (repository.indexOf(urlSuffix) > -1) {
				repository = repository.substring(0, repository.indexOf(urlSuffix));
			}
		}
		return repository;
	}

	/**
	 * Extract the repository name from the url.
	 * 
	 * @param url
	 * @return repository name
	 */
	@Override
	protected String extractRepositoryName(String url) {
		return GitFilter.getRepositoryName(url);
	}

	/**
	 * Analyze the url and returns the action of the request. Return values are
	 * either "/git-receive-pack" or "/git-upload-pack".
	 * 
	 * @param serverUrl
	 * @return action of the request
	 */
	@Override
	protected String getUrlRequestAction(String suffix) {
		if (!StringUtils.isEmpty(suffix)) {
			if (suffix.startsWith(gitReceivePack)) {
				return gitReceivePack;
			} else if (suffix.startsWith(gitUploadPack)) {
				return gitUploadPack;
			} else if (suffix.contains("?service=git-receive-pack")) {
				return gitReceivePack;
			} else if (suffix.contains("?service=git-upload-pack")) {
				return gitUploadPack;
			} else {
				return gitUploadPack;
			}
		}
		return null;
	}
	
	/**
	 * Determine if a non-existing repository can be created using this filter.
	 *  
	 * @return true if the server allows repository creation on-push
	 */
	@Override
	protected boolean isCreationAllowed() {
		return GitBlit.getBoolean(Keys.git.allowCreateOnPush, true);
	}
	
	/**
	 * Determine if the repository can receive pushes.
	 * 
	 * @param repository
	 * @param action
	 * @return true if the action may be performed
	 */
	@Override
	protected boolean isActionAllowed(RepositoryModel repository, String action) {
		// the log here has been moved into ReceiveHook to provide clients with
		// error messages
		return true;
	}

	@Override
	protected boolean requiresClientCertificate() {
		return GitBlit.getBoolean(Keys.git.requiresClientCertificate, false);
	}

	/**
	 * Determine if the repository requires authentication.
	 * 
	 * @param repository
	 * @param action
	 * @return true if authentication required
	 */
	@Override
	protected boolean requiresAuthentication(RepositoryModel repository, String action) {
		if (gitUploadPack.equals(action)) {
			// send to client
			return repository.accessRestriction.atLeast(AccessRestrictionType.CLONE);	
		} else if (gitReceivePack.equals(action)) {
			// receive from client
			return repository.accessRestriction.atLeast(AccessRestrictionType.PUSH);
		}
		return false;
	}

	/**
	 * Determine if the user can access the repository and perform the specified
	 * action.
	 * 
	 * @param repository
	 * @param user
	 * @param action
	 * @return true if user may execute the action on the repository
	 */
	@Override
	protected boolean canAccess(RepositoryModel repository, UserModel user, String action) {
		if (!GitBlit.getBoolean(Keys.git.enableGitServlet, true)) {
			// Git Servlet disabled
			return false;
		}		
		if (action.equals(gitReceivePack)) {
			// Push request
			if (user.canPush(repository)) {
				return true;
			} else {
				// user is unauthorized to push to this repository
				logger.warn(MessageFormat.format("user {0} is not authorized to push to {1}",
						user.username, repository));
				return false;
			}
		} else if (action.equals(gitUploadPack)) {
			// Clone request
			if (user.canClone(repository)) {
				return true;
			} else {
				// user is unauthorized to clone this repository
				logger.warn(MessageFormat.format("user {0} is not authorized to clone {1}",
						user.username, repository));
				return false;
			}
		}
		return true;
	}
	
	/**
	 * An authenticated user with the CREATE role can create a repository on
	 * push.
	 * 
	 * @param user
	 * @param repository
	 * @param action
	 * @return the repository model, if it is created, null otherwise
	 */
	@Override
	protected RepositoryModel createRepository(UserModel user, String repository, String action) {
		boolean isPush = !StringUtils.isEmpty(action) && gitReceivePack.equals(action);
		if (isPush) {
			if (user.canCreate(repository)) {
				// user is pushing to a new repository
				// validate name
				if (repository.startsWith("../")) {
					logger.error(MessageFormat.format("Illegal relative path in repository name! {0}", repository));
					return null;
				}
				if (repository.contains("/../")) {
					logger.error(MessageFormat.format("Illegal relative path in repository name! {0}", repository));
					return null;
				}					

				// confirm valid characters in repository name
				Character c = StringUtils.findInvalidCharacter(repository);
				if (c != null) {
					logger.error(MessageFormat.format("Invalid character '{0}' in repository name {1}!", c, repository));
					return null;
				}

				// create repository
				RepositoryModel model = new RepositoryModel();
				model.name = repository;
				model.addOwner(user.username);
				model.projectPath = StringUtils.getFirstPathElement(repository);
				if (model.isUsersPersonalRepository(user.username)) {
					// personal repository, default to private for user
					model.authorizationControl = AuthorizationControl.NAMED;
					model.accessRestriction = AccessRestrictionType.VIEW;
				} else {
					// common repository, user default server settings
					model.authorizationControl = AuthorizationControl.fromName(GitBlit.getString(Keys.git.defaultAuthorizationControl, ""));
					model.accessRestriction = AccessRestrictionType.fromName(GitBlit.getString(Keys.git.defaultAccessRestriction, ""));
				}

				// create the repository
				try {
					GitBlit.self().updateRepositoryModel(model.name, model, true);
					logger.info(MessageFormat.format("{0} created {1} ON-PUSH", user.username, model.name));
					return GitBlit.self().getRepositoryModel(model.name);
				} catch (GitBlitException e) {
					logger.error(MessageFormat.format("{0} failed to create repository {1} ON-PUSH!", user.username, model.name), e);
				}
			} else {
				logger.warn(MessageFormat.format("{0} is not permitted to create repository {1} ON-PUSH!", user.username, repository));
			}
		}
		
		// repository could not be created or action was not a push
		return null;
	}
}
