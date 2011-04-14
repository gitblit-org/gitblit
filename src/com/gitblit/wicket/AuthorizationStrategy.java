package com.gitblit.wicket;

import org.apache.wicket.Component;
import org.apache.wicket.RestartResponseAtInterceptPageException;
import org.apache.wicket.authorization.IUnauthorizedComponentInstantiationListener;
import org.apache.wicket.authorization.strategies.page.AbstractPageAuthorizationStrategy;

import com.gitblit.wicket.pages.RepositoriesPage;

public class AuthorizationStrategy extends AbstractPageAuthorizationStrategy implements IUnauthorizedComponentInstantiationListener {

	public AuthorizationStrategy() {
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	protected boolean isPageAuthorized(Class pageClass) {
		if (BasePage.class.isAssignableFrom(pageClass))
			return isAuthorized(pageClass);
		// Return contruction by default
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

	protected boolean isAuthorized(Class<? extends BasePage> pageClass) {
		GitBlitWebSession session = GitBlitWebSession.get();
		if (!session.isLoggedIn())
			return false;
		User user = session.getUser();
		if (pageClass.isAnnotationPresent(AdminPage.class)) {

		}
		return true;
	}
}
