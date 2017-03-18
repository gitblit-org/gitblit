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
package com.gitblit.servlet;

import java.io.UnsupportedEncodingException;
import java.text.MessageFormat;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import javax.servlet.http.HttpServletRequest;

import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.Constants.AuthorizationControl;
import com.gitblit.GitBlitException;
import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.manager.IAuthenticationManager;
import com.gitblit.manager.IFederationManager;
import com.gitblit.manager.IRepositoryManager;
import com.gitblit.manager.IRuntimeManager;
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
@Singleton
public class GitFilter extends AccessRestrictionFilter {

	protected static final String gitReceivePack = "/git-receive-pack";

	protected static final String gitUploadPack = "/git-upload-pack";
	
	protected static final String gitLfs = "/info/lfs";
	
	protected static final String[] suffixes = { gitReceivePack, gitUploadPack, "/info/refs", "/HEAD",
			"/objects", gitLfs };

	private IStoredSettings settings;

	private IFederationManager federationManager;

	@Inject
	public GitFilter(
			IStoredSettings settings,
			IRuntimeManager runtimeManager,
			IAuthenticationManager authenticationManager,
			IRepositoryManager repositoryManager,
			IFederationManager federationManager) {

		super(runtimeManager, authenticationManager, repositoryManager);

		this.settings = settings;
		this.federationManager = federationManager;
	}

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
		try {
			repository = java.net.URLDecoder.decode(repository, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			
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
	 * Analyze the url and returns the action of the request. Return values are:
	 * "/git-receive-pack", "/git-upload-pack" or "/info/lfs".
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
			} else if (suffix.startsWith(gitLfs)) {
				return gitLfs;
			} else {
				return gitUploadPack;
			}
		}
		return null;
	}

	/**
	 * Returns the user making the request, if the user has authenticated.
	 *
	 * @param httpRequest
	 * @return user
	 */
	@Override
	protected UserModel getUser(HttpServletRequest httpRequest) {
		UserModel user = authenticationManager.authenticate(httpRequest, requiresClientCertificate());
		if (user == null) {
			user = federationManager.authenticate(httpRequest);
		}
		return user;
	}

	/**
	 * Determine if a non-existing repository can be created using this filter.
	 *
	 * @return true if the server allows repository creation on-push
	 */
	@Override
	protected boolean isCreationAllowed(String action) {
		
		//Repository must already exist before large files can be deposited
		if (action.equals(gitLfs)) {
			return false;
		}
		
		return settings.getBoolean(Keys.git.allowCreateOnPush, true);
	}

	/**
	 * Determine if the repository can receive pushes.
	 *
	 * @param repository
	 * @param action
	 * @return true if the action may be performed
	 */
	@Override
	protected boolean isActionAllowed(RepositoryModel repository, String action, String method) {
		// the log here has been moved into ReceiveHook to provide clients with
		// error messages
		if (gitLfs.equals(action)) {
			if (!method.matches("GET|POST|PUT|HEAD")) {
				return false;
			}
		}
		
		return true;
	}

	@Override
	protected boolean requiresClientCertificate() {
		return settings.getBoolean(Keys.git.requiresClientCertificate, false);
	}

	/**
	 * Determine if the repository requires authentication.
	 *
	 * @param repository
	 * @param action
	 * @param method
	 * @return true if authentication required
	 */
	@Override
	protected boolean requiresAuthentication(RepositoryModel repository, String action, String method) {
		if (gitUploadPack.equals(action)) {
			// send to client
			return repository.accessRestriction.atLeast(AccessRestrictionType.CLONE);
		} else if (gitReceivePack.equals(action)) {
			// receive from client
			return repository.accessRestriction.atLeast(AccessRestrictionType.PUSH);
		} else if (gitLfs.equals(action)) {
			
			if (method.matches("GET|HEAD")) {
				return repository.accessRestriction.atLeast(AccessRestrictionType.CLONE);
			} else {
				//NOTE: Treat POST as PUT as as without reading message type cannot determine 
				return repository.accessRestriction.atLeast(AccessRestrictionType.PUSH);
			}
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
		if (!settings.getBoolean(Keys.git.enableGitServlet, true)) {
			// Git Servlet disabled
			return false;
		}
		if (action.equals(gitReceivePack)) {
			// push permissions are enforced in the receive pack
			return true;
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
		
		if (action.equals(gitLfs)) {
			//Repository must already exist for any filestore actions
			return null;
		}
		
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
					model.authorizationControl = AuthorizationControl.fromName(settings.getString(Keys.git.defaultAuthorizationControl, ""));
					model.accessRestriction = AccessRestrictionType.fromName(settings.getString(Keys.git.defaultAccessRestriction, "PUSH"));
				}

				// create the repository
				try {
					repositoryManager.updateRepositoryModel(model.name, model, true);
					logger.info(MessageFormat.format("{0} created {1} ON-PUSH", user.username, model.name));
					return repositoryManager.getRepositoryModel(model.name);
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
	
	/**
	 * Git lfs action uses an alternative authentication header, 
	 * dependent on the viewing method.
	 * 
	 * @param httpRequest
	 * @param action
	 * @return
	 */
	@Override
	protected String getAuthenticationHeader(HttpServletRequest httpRequest, String action) {

		if (action.equals(gitLfs)) {
			if (hasContentInRequestHeader(httpRequest, "Accept", FilestoreServlet.GIT_LFS_META_MIME)) {
				return "LFS-Authenticate";
			}
		}
		
		return super.getAuthenticationHeader(httpRequest, action);
	}
	
	/**
	 * Interrogates the request headers based on the action
	 * @param action
	 * @param request
	 * @return
	 */
	@Override
	protected boolean hasValidRequestHeader(String action,
			HttpServletRequest request) {

		if (action.equals(gitLfs) && request.getMethod().equals("POST")) {
			if ( 	!hasContentInRequestHeader(request, "Accept", FilestoreServlet.GIT_LFS_META_MIME)
				 || !hasContentInRequestHeader(request, "Content-Type", FilestoreServlet.GIT_LFS_META_MIME)) {
				return false;
			}				
		}
			
		return super.hasValidRequestHeader(action, request);
	}
}
