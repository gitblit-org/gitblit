/*
 * Copyright 2013 gitblit.com.
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

import java.util.HashMap;

import org.junit.Test;

import com.gitblit.manager.AuthenticationManager;
import com.gitblit.manager.IAuthenticationManager;
import com.gitblit.manager.IUserManager;
import com.gitblit.manager.RuntimeManager;
import com.gitblit.manager.UserManager;
import com.gitblit.models.UserModel;
import com.gitblit.tests.mock.MemorySettings;
import com.gitblit.utils.XssFilter;
import com.gitblit.utils.XssFilter.AllowXssFilter;

/**
 * Class for testing local authentication.
 *
 * @author James Moger
 *
 */
public class AuthenticationManagerTest extends GitblitUnitTest {

	IUserManager users;

    MemorySettings getSettings() {
    	return new MemorySettings(new HashMap<String, Object>());
    }

    IAuthenticationManager newAuthenticationManager() {
    	XssFilter xssFilter = new AllowXssFilter();
    	RuntimeManager runtime = new RuntimeManager(getSettings(), xssFilter, GitBlitSuite.BASEFOLDER).start();
    	users = new UserManager(runtime, null).start();
    	AuthenticationManager auth = new AuthenticationManager(runtime, users).start();
    	return auth;
    }

    @Test
    public void testAuthenticate() throws Exception {
    	IAuthenticationManager auth = newAuthenticationManager();

    	UserModel user = new UserModel("sunnyjim");
		user.password = "password";
		users.updateUserModel(user);

		assertNotNull(auth.authenticate(user.username, user.password.toCharArray()));
		user.disabled = true;

		users.updateUserModel(user);
		assertNull(auth.authenticate(user.username, user.password.toCharArray()));
		users.deleteUserModel(user);
    }
}
