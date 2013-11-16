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
package com.gitblit.wicket;

import org.apache.wicket.Component;
import org.apache.wicket.RestartResponseException;
import org.apache.wicket.authorization.IUnauthorizedComponentInstantiationListener;
import org.apache.wicket.authorization.strategies.page.AbstractPageAuthorizationStrategy;
import org.apache.wicket.markup.html.WebPage;

import com.gitblit.GitBlit;
import com.gitblit.Keys;
import com.gitblit.models.UserModel;
import com.gitblit.wicket.pages.BasePage;

public class AuthorizationStrategy extends AbstractPageAuthorizationStrategy implements
		IUnauthorizedComponentInstantiationListener {

	Class<? extends WebPage> homepageClass;

	public AuthorizationStrategy(Class<? extends WebPage> homepageClass) {
		this.homepageClass = homepageClass;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	protected boolean isPageAuthorized(Class pageClass) {
		if (homepageClass.equals(pageClass)) {
			// allow all requests to get to the HomePage with its inline
			// authentication form
			return true;
		}

		if (BasePage.class.isAssignableFrom(pageClass)) {
			boolean authenticateView = GitBlit.getBoolean(Keys.web.authenticateViewPages, true);
			boolean authenticateAdmin = GitBlit.getBoolean(Keys.web.authenticateAdminPages, true);
			boolean allowAdmin = GitBlit.getBoolean(Keys.web.allowAdministration, true);

			GitBlitWebSession session = GitBlitWebSession.get();
			if (authenticateView && !session.isLoggedIn()) {
				// authentication required
				session.cacheRequest(pageClass);
				return false;
			}

			UserModel user = session.getUser();
			if (pageClass.isAnnotationPresent(RequiresAdminRole.class)) {
				// admin page
				if (allowAdmin) {
					if (authenticateAdmin) {
						// authenticate admin
						if (user != null) {
							return user.canAdmin();
						}
						return false;
					} else {
						// no admin authentication required
						return true;
					}
				} else {
					// admin prohibited
					return false;
				}
			}
		}
		return true;
	}

	@Override
	public void onUnauthorizedInstantiation(Component component) {

		if (component instanceof BasePage) {
			throw new RestartResponseException(homepageClass);
		}
	}
}
