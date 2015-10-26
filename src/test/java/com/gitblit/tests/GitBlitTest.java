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
import java.util.List;

import org.eclipse.jgit.lib.Repository;
import org.junit.Test;

import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.FileSettings;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.FileUtils;

public class GitBlitTest extends GitblitUnitTest {

	@Test
	public void testRepositoryModel() throws Exception {
		List<String> repositories = repositories().getRepositoryList();
		assertTrue("Repository list is empty!", repositories.size() > 0);
		assertTrue(
				"Missing Helloworld repository!",
				repositories.contains(GitBlitSuite.getHelloworldRepository().getDirectory()
						.getName()));
		Repository r = GitBlitSuite.getHelloworldRepository();
		RepositoryModel model = repositories().getRepositoryModel(r.getDirectory().getName());
		assertTrue("Helloworld model is null!", model != null);
		assertEquals(GitBlitSuite.getHelloworldRepository().getDirectory().getName(), model.name);
		assertTrue(repositories().updateLastChangeFields(r, model) > 22000L);
		r.close();
	}

	@Test
	public void testUserModel() throws Exception {
		List<String> users = users().getAllUsernames();
		assertTrue("No users found!", users.size() > 0);
		assertTrue("Admin not found", users.contains("admin"));
		UserModel user = users().getUserModel("admin");
		assertEquals("admin", user.toString());
		assertTrue("Admin missing #admin role!", user.canAdmin);
		user.canAdmin = false;
		assertFalse("Admin should not have #admin!", user.canAdmin);
		String repository = GitBlitSuite.getHelloworldRepository().getDirectory().getName();
		RepositoryModel repositoryModel = repositories().getRepositoryModel(repository);
		repositoryModel.accessRestriction = AccessRestrictionType.VIEW;
		assertFalse("Admin can still access repository!",
				user.canView(repositoryModel));
		user.addRepositoryPermission(repository);
		assertTrue("Admin can't access repository!", user.canView(repositoryModel));
		assertEquals(repositories().getRepositoryModel(user, "pretend"), null);
		assertNotNull(repositories().getRepositoryModel(user, repository));
		assertTrue(repositories().getRepositoryModels(user).size() > 0);
	}

	@Test
	public void testUserModelVerification() throws Exception {
		UserModel user = new UserModel("james");
		user.displayName = "James Moger";

		assertFalse(user.is("James", null));
		assertFalse(user.is("James", ""));
		assertFalse(user.is("JaMeS", "anything"));

		assertFalse(user.is("james moger", null));
		assertFalse(user.is("james moger", ""));
		assertFalse(user.is("james moger", "anything"));

		assertFalse(user.is("joe", null));
		assertFalse(user.is("joe", ""));
		assertFalse(user.is("joe", "anything"));

		// specify email address which results in address verification
		user.emailAddress = "something";

		assertFalse(user.is("James", null));
		assertFalse(user.is("James", ""));
		assertFalse(user.is("JaMeS", "anything"));

		assertFalse(user.is("james moger", null));
		assertFalse(user.is("james moger", ""));
		assertFalse(user.is("james moger", "anything"));

		assertTrue(user.is("JaMeS", user.emailAddress));
		assertTrue(user.is("JaMeS mOgEr", user.emailAddress));
	}

	@Test
	public void testAccessRestrictionTypes() throws Exception {
		assertTrue(AccessRestrictionType.PUSH.exceeds(AccessRestrictionType.NONE));
		assertTrue(AccessRestrictionType.CLONE.exceeds(AccessRestrictionType.PUSH));
		assertTrue(AccessRestrictionType.VIEW.exceeds(AccessRestrictionType.CLONE));

		assertFalse(AccessRestrictionType.NONE.exceeds(AccessRestrictionType.PUSH));
		assertFalse(AccessRestrictionType.PUSH.exceeds(AccessRestrictionType.CLONE));
		assertFalse(AccessRestrictionType.CLONE.exceeds(AccessRestrictionType.VIEW));

		assertTrue(AccessRestrictionType.PUSH.atLeast(AccessRestrictionType.NONE));
		assertTrue(AccessRestrictionType.CLONE.atLeast(AccessRestrictionType.PUSH));
		assertTrue(AccessRestrictionType.VIEW.atLeast(AccessRestrictionType.CLONE));

		assertFalse(AccessRestrictionType.NONE.atLeast(AccessRestrictionType.PUSH));
		assertFalse(AccessRestrictionType.PUSH.atLeast(AccessRestrictionType.CLONE));
		assertFalse(AccessRestrictionType.CLONE.atLeast(AccessRestrictionType.VIEW));

		assertTrue(AccessRestrictionType.PUSH.toString().equals("PUSH"));
		assertTrue(AccessRestrictionType.CLONE.toString().equals("CLONE"));
		assertTrue(AccessRestrictionType.VIEW.toString().equals("VIEW"));

		assertEquals(AccessRestrictionType.NONE, AccessRestrictionType.fromName("none"));
		assertEquals(AccessRestrictionType.PUSH, AccessRestrictionType.fromName("push"));
		assertEquals(AccessRestrictionType.CLONE, AccessRestrictionType.fromName("clone"));
		assertEquals(AccessRestrictionType.VIEW, AccessRestrictionType.fromName("view"));
	}

	@Test
	public void testFileSettings() throws Exception {
		FileSettings settings = new FileSettings("src/main/distrib/data/gitblit.properties");
		assertEquals(true, settings.getBoolean("missing", true));
		assertEquals("default", settings.getString("missing", "default"));
		assertEquals(10, settings.getInteger("missing", 10));
		assertEquals(5, settings.getInteger("realm.realmFile", 5));

		assertTrue(settings.getBoolean("git.enableGitServlet", false));
		assertEquals("${baseFolder}/users.conf", settings.getString("realm.userService", null));
		assertEquals(5, settings.getInteger("realm.minPasswordLength", 0));
		List<String> mdExtensions = settings.getStrings("web.markdownExtensions");
		assertTrue(mdExtensions.size() > 0);
		assertTrue(mdExtensions.contains("md"));

		List<String> keys = settings.getAllKeys("server");
		assertTrue(keys.size() > 0);
		assertTrue(keys.contains("server.httpsPort"));

		assertTrue(settings.getChar("web.forwardSlashCharacter", ' ') == '/');
	}

	@Test
	public void testGitblitSettings() throws Exception {
		// These are already tested by above test method.
		assertTrue(settings().getBoolean("missing", true));
		assertEquals("default", settings().getString("missing", "default"));
		assertEquals(10, settings().getInteger("missing", 10));
		assertEquals(5, settings().getInteger("realm.userService", 5));

		assertTrue(settings().getBoolean("git.enableGitServlet", false));
		File userDir = new File(System.getProperty("user.dir"));
		File userService = new File(settings().getString("realm.userService", null));
		assertEquals("src/test/config/test-users.conf", FileUtils.getRelativePath(userDir, userService));
		assertEquals(5, settings().getInteger("realm.minPasswordLength", 0));
		List<String> mdExtensions = settings().getStrings("web.markdownExtensions");
		assertTrue(mdExtensions.size() > 0);
		assertTrue(mdExtensions.contains("md"));

		List<String> keys = settings().getAllKeys("server");
		assertTrue(keys.size() > 0);
		assertTrue(keys.contains("server.httpsPort"));

		assertTrue(settings().getChar("web.forwardSlashCharacter", ' ') == '/');
		assertFalse(runtime().isDebugMode());
	}

	@Test
	public void testAuthentication() throws Exception {
		assertTrue(authentication().authenticate("admin", "admin".toCharArray(), null) != null);
	}

	@Test
	public void testRepositories() throws Exception {
		assertTrue(repositories().getRepository("missing") == null);
		assertTrue(repositories().getRepositoryModel("missing") == null);
	}
}
