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
package com.gitblit.tests;

import java.io.File;
import java.io.IOException;

import junit.framework.TestCase;

import com.gitblit.ConfigUserService;
import com.gitblit.FileUserService;
import com.gitblit.IUserService;
import com.gitblit.models.UserModel;

public class UserServiceTest extends TestCase {

	public void testFileUserService() throws IOException {
		File file = new File("us-test.properties");
		file.delete();
		test(new FileUserService(file));
		file.delete();
	}

	public void testConfigUserService() throws IOException {
		File file = new File("us-test.conf");
		file.delete();
		test(new ConfigUserService(file));
		file.delete();
	}

	protected void test(IUserService service) {

		UserModel admin = service.getUserModel("admin");
		assertTrue(admin == null);

		// add admin
		admin = new UserModel("admin");
		admin.password = "password";
		admin.canAdmin = true;
		admin.excludeFromFederation = true;
		service.updateUserModel(admin);
		admin = null;

		// add new user
		UserModel newUser = new UserModel("test");
		newUser.password = "testPassword";
		newUser.addRepository("repo1");
		newUser.addRepository("repo2");
		newUser.addRepository("sub/repo3");
		service.updateUserModel(newUser);

		// add one more new user and then test reload of first new user
		newUser = new UserModel("garbage");
		newUser.password = "garbage";
		service.updateUserModel(newUser);

		// confirm all added users
		assertEquals(3, service.getAllUsernames().size());

		// confirm reloaded test user
		newUser = service.getUserModel("test");
		assertEquals("testPassword", newUser.password);
		assertEquals(3, newUser.repositories.size());
		assertTrue(newUser.hasRepository("repo1"));
		assertTrue(newUser.hasRepository("repo2"));
		assertTrue(newUser.hasRepository("sub/repo3"));

		// confirm authentication of test user
		UserModel testUser = service.authenticate("test", "testPassword".toCharArray());
		assertEquals("test", testUser.username);
		assertEquals("testPassword", testUser.password);

		// delete a repository role and confirm role removal from test user
		service.deleteRepositoryRole("repo2");
		testUser = service.getUserModel("test");
		assertEquals(2, testUser.repositories.size());

		// delete garbage user and confirm user count
		service.deleteUser("garbage");
		assertEquals(2, service.getAllUsernames().size());

		// rename repository and confirm role change for test user
		service.renameRepositoryRole("repo1", "newrepo1");
		testUser = service.getUserModel("test");
		assertTrue(testUser.hasRepository("newrepo1"));
	}
}