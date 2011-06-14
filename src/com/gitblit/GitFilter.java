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
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.StringUtils;

public class GitFilter extends AccessRestrictionFilter {

	protected final String gitReceivePack = "/git-receive-pack";

	protected final String gitUploadPack = "/git-upload-pack";

	protected final String[] suffixes = { gitReceivePack, gitUploadPack, "/info/refs", "/HEAD",
			"/objects" };

	@Override
	protected String extractRepositoryName(String url) {
		String repository = url;
		for (String urlSuffix : suffixes) {
			if (repository.indexOf(urlSuffix) > -1) {
				repository = repository.substring(0, repository.indexOf(urlSuffix));
			}
		}
		return repository;
	}

	@Override
	protected String getUrlRequestType(String suffix) {
		if (!StringUtils.isEmpty(suffix)) {
			if (suffix.startsWith(gitReceivePack)) {
				return gitReceivePack;
			} else if (suffix.startsWith(gitUploadPack)) {
				return gitUploadPack;
			} else if (suffix.contains("?service=git-receive-pack")) {
				return gitReceivePack;
			} else if (suffix.contains("?service=git-upload-pack")) {
				return gitUploadPack;
			}
		}
		return null;
	}

	@Override
	protected boolean requiresAuthentication(RepositoryModel repository) {
		return repository.accessRestriction.atLeast(AccessRestrictionType.PUSH);
	}

	@Override
	protected boolean canAccess(RepositoryModel repository, UserModel user, String urlRequestType) {
		if (repository.isFrozen || repository.accessRestriction.atLeast(AccessRestrictionType.PUSH)) {
			boolean authorizedUser = user.canAccessRepository(repository.name);
			if (urlRequestType.equals(gitReceivePack)) {
				// Push request
				if (!repository.isFrozen && authorizedUser) {
					// clone-restricted or push-authorized
					return true;
				} else {
					// user is unauthorized to push to this repository
					logger.warn(MessageFormat.format("user {0} is not authorized to push to {1}",
							user.username, repository));
					return false;
				}
			} else if (urlRequestType.equals(gitUploadPack)) {
				// Clone request
				boolean cloneRestricted = repository.accessRestriction
						.atLeast(AccessRestrictionType.CLONE);
				if (!cloneRestricted || (cloneRestricted && authorizedUser)) {
					// push-restricted or clone-authorized
					return true;
				} else {
					// user is unauthorized to clone this repository
					logger.warn(MessageFormat.format("user {0} is not authorized to clone {1}",
							user.username, repository));
					return false;
				}
			}
		}
		return true;
	}
}
