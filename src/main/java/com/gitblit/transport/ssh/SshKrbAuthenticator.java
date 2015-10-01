/*
 * Copyright 2015 gitblit.com.
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
package com.gitblit.transport.ssh;

import com.gitblit.manager.IAuthenticationManager;
import com.gitblit.models.UserModel;
import java.util.Locale;
import org.apache.sshd.server.auth.gss.GSSAuthenticator;
import org.apache.sshd.server.session.ServerSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SshKrbAuthenticator extends GSSAuthenticator {
	
	protected final Logger log = LoggerFactory.getLogger(getClass());
	protected final IAuthenticationManager authManager;
	protected final boolean stripDomain;

	public SshKrbAuthenticator(IAuthenticationManager authManager, boolean stripDomain) {
		this.authManager = authManager;
		this.stripDomain = stripDomain;
		log.info("registry {}", authManager);
	}

	public boolean validateIdentity(ServerSession session, String identity) {
		log.info("identify with kerberos {}", identity);
		SshDaemonClient client = (SshDaemonClient)session.getAttribute(SshDaemonClient.KEY);
		if (client.getUser() != null) {
			log.info("{} has already authenticated!", identity);
			return true;
		}
		String username = identity.toLowerCase(Locale.US);
		if (stripDomain) {
			int p = username.indexOf('@');
			if (p > 0)
				username = username.substring(0, p);
		}
		UserModel user = authManager.authenticate(username);
		if (user != null) {
			client.setUser(user);
			return true;
		}
		log.warn("could not authenticate {} for SSH", username);
		return false;
	}
}
