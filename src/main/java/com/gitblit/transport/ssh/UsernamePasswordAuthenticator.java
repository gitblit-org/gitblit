/*
 * Copyright 2014 gitblit.com.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.gitblit.transport.ssh;

import java.util.Locale;

import org.apache.sshd.server.auth.password.PasswordAuthenticator;
import org.apache.sshd.server.session.ServerSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.manager.IAuthenticationManager;
import com.gitblit.models.UserModel;

/**
 *
 * Authenticates an SSH session with username/password credentials.
 *
 * @author James Moger
 *
 */
public class UsernamePasswordAuthenticator implements PasswordAuthenticator {

	protected final Logger log = LoggerFactory.getLogger(getClass());

	protected final IAuthenticationManager authManager;

	public UsernamePasswordAuthenticator(IAuthenticationManager authManager) {
		this.authManager = authManager;
	}

	@Override
	public boolean authenticate(String username, String password, ServerSession session) {
		SshDaemonClient client = session.getAttribute(SshDaemonClient.KEY);
		if (client.getUser() != null) {
			log.info("{} has already authenticated!", username);
			return true;
		}

		username = username.toLowerCase(Locale.US);
		UserModel user = authManager.authenticate(username, password.toCharArray());
		if (user != null) {
			client.setUser(user);
			return true;
		}

		log.warn("could not authenticate {} ({}) for SSH using the supplied password", username, client.getRemoteAddress());
		return false;
	}
}
