package com.gitblit.wicket;

import org.apache.wicket.Component;
import org.apache.wicket.RestartResponseAtInterceptPageException;
import org.apache.wicket.authorization.IUnauthorizedComponentInstantiationListener;
import org.apache.wicket.authorization.strategies.page.AbstractPageAuthorizationStrategy;

import com.gitblit.GitBlit;
import com.gitblit.Keys;
import com.gitblit.wicket.models.UserModel;
import com.gitblit.wicket.pages.RepositoriesPage;

public class AuthorizationStrategy extends AbstractPageAuthorizationStrategy implements IUnauthorizedComponentInstantiationListener {

	public AuthorizationStrategy() {
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	protected boolean isPageAuthorized(Class pageClass) {
		if (BasePage.class.isAssignableFrom(pageClass)) {
			boolean authenticateView = GitBlit.self().settings().getBoolean(Keys.web.authenticateViewPages, true);
			boolean authenticateAdmin = GitBlit.self().settings().getBoolean(Keys.web.authenticateAdminPages, true);
			boolean allowAdmin = GitBlit.self().settings().getBoolean(Keys.web.allowAdministration, true);
			
			GitBlitWebSession session = GitBlitWebSession.get();			
			if (authenticateView && !session.isLoggedIn()) {
				// authentication required
				return false;
			}
			
			UserModel user = session.getUser();
			if (pageClass.isAnnotationPresent(AdminPage.class)) {
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
					//admin prohibited
					return false;
				}
			}
		}
		return true;
	}

	@Override
	public void onUnauthorizedInstantiation(Component component) {
		if (component instanceof BasePage) {
			GitBlitWebSession session = GitBlitWebSession.get();
			if (!session.isLoggedIn())
				throw new RestartResponseAtInterceptPageException(LoginPage.class);
			else
				throw new RestartResponseAtInterceptPageException(RepositoriesPage.class);
		}
	}
}
