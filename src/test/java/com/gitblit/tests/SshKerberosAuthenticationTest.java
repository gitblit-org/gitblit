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
package com.gitblit.tests;

import org.apache.sshd.server.auth.gss.GSSAuthenticator;
import org.apache.sshd.server.session.ServerSession;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.gitblit.manager.AuthenticationManager;
import com.gitblit.manager.IRuntimeManager;
import com.gitblit.manager.IUserManager;
import com.gitblit.models.UserModel;
import com.gitblit.tests.mock.MemorySettings;
import com.gitblit.transport.ssh.SshDaemonClient;
import com.gitblit.transport.ssh.SshKrbAuthenticator;

public class SshKerberosAuthenticationTest extends GitblitUnitTest {

	private static class UserModelWrapper {
		public UserModel um;
	}

	@Test
	public void testUserManager() {
		IRuntimeManager rm = Mockito.mock(IRuntimeManager.class);

		//Build an UserManager that can build a UserModel
		IUserManager im = Mockito.mock(IUserManager.class);
		Mockito.doAnswer(new Answer<Object>() {
			@Override
			public Object answer(InvocationOnMock invocation) {
				Object[] args = invocation.getArguments();
				String user = (String) args[0];
				return new UserModel(user);
			}
		}).when(im).getUserModel(Mockito.anyString());

		AuthenticationManager am = new AuthenticationManager(rm, im);

		GSSAuthenticator gssAuthenticator = new SshKrbAuthenticator(new MemorySettings(), am);

		ServerSession session = Mockito.mock(ServerSession.class);

		//Build an SshDaemonClient that can set and get the UserModel
		final UserModelWrapper umw = new UserModelWrapper();
		SshDaemonClient client = Mockito.mock(SshDaemonClient.class);
		Mockito.when(client.getUser()).thenReturn(umw.um);
		Mockito.doAnswer(new Answer<Object>() {
			@Override
			public Object answer(InvocationOnMock invocation) {
				Object[] args = invocation.getArguments();
				UserModel um = (UserModel) args[0];
				umw.um = um;
				return null;
			}
		}).when(client).setUser(Mockito.any(UserModel.class));

		Mockito.when(session.getAttribute(SshDaemonClient.KEY)).thenReturn(client);
		Assert.assertTrue(gssAuthenticator.validateIdentity(session, "jhappy"));

	}
}
