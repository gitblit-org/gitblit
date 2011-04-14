package com.gitblit;

import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.server.UserIdentity;

import com.gitblit.wicket.User;

public class JettyLoginService extends HashLoginService implements ILoginService {

	public JettyLoginService(String realmFile) {
		super(Constants.NAME, realmFile);
	}

	@Override
	public User authenticate(String username, char[] password) {
		UserIdentity identity = login(username, new String(password));
		if (identity == null || identity.equals(UserIdentity.UNAUTHENTICATED_IDENTITY)) {
			return null;
		}
		User user = new User(username, password);
		user.canAdmin(identity.isUserInRole(Constants.ADMIN_ROLE, null));
		user.canClone(identity.isUserInRole(Constants.PULL_ROLE, null));
		user.canPush(identity.isUserInRole(Constants.PUSH_ROLE, null));
		return user;
	}

	@Override
	public User authenticate(char[] cookie) {
		// TODO cookie login
		return null;
	}
}
