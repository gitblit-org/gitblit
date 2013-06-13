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
package com.gitblit.transport.ssh;

import java.security.PublicKey;

import org.apache.sshd.server.PublickeyAuthenticator;
import org.apache.sshd.server.session.ServerSession;

import com.gitblit.manager.IGitblit;

/**
 *
 * @author Eric Myrhe
 *
 */
public class SshKeyAuthenticator implements PublickeyAuthenticator {

	protected final IGitblit gitblit;

	public SshKeyAuthenticator(IGitblit gitblit) {
		this.gitblit = gitblit;
	}

	@Override
	public boolean authenticate(String username, PublicKey key, ServerSession session) {
		// TODO actually authenticate
		return true;
	}
}
