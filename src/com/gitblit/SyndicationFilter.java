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

import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;

public class SyndicationFilter extends AccessRestrictionFilter {

	@Override
	protected String extractRepositoryName(String url) {
		return url;
	}

	@Override
	protected String getUrlRequestType(String url) {
		return "RESTRICTED";
	}

	@Override
	protected boolean requiresAuthentication(RepositoryModel repository) {
		return repository.accessRestriction.atLeast(AccessRestrictionType.VIEW);
	}

	@Override
	protected boolean canAccess(RepositoryModel repository, UserModel user, String restrictedURL) {
		return user.canAccessRepository(repository.name);
	}

}
