/*
 * Copyright 2014 gitblit.com.
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
package com.gitblit.tests;

import java.security.PublicKey;

import org.apache.sshd.server.session.ServerSession;

import com.gitblit.manager.IAuthenticationManager;
import com.gitblit.transport.ssh.CachingPublicKeyAuthenticator;
import com.gitblit.transport.ssh.IKeyManager;

public class BogusPublicKeyAuthenticator extends CachingPublicKeyAuthenticator {

	public BogusPublicKeyAuthenticator(IKeyManager keyManager,
			IAuthenticationManager authManager) {
		super(keyManager, authManager);
	}

	@Override
	protected boolean doAuthenticate(String username, PublicKey suppliedKey,
			ServerSession session) {
		// TODO(davido): put authenticated user in session
		return true;
	}
}
