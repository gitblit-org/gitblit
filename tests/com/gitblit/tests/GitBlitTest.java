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

import java.util.List;

import junit.framework.TestCase;

import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.FileSettings;
import com.gitblit.GitBlit;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;

public class GitBlitTest extends TestCase {

	public void testRepositoryModel() throws Exception {
		List<String> repositories = GitBlit.self().getRepositoryList();
		assertTrue("Repository list is empty!", repositories.size() > 0);
		assertTrue(
				"Missing Helloworld repository!",
				repositories.contains(GitBlitSuite.getHelloworldRepository().getDirectory()
						.getName()));
		RepositoryModel model = GitBlit.self().getRepositoryModel(
				GitBlitSuite.getHelloworldRepository().getDirectory().getName());
		assertTrue("Helloworld model is null!", model != null);
		assertTrue(model.toString().equals(
				GitBlitSuite.getHelloworldRepository().getDirectory().getName()));
		assertTrue(GitBlit.self().calculateSize(model) > 22000L);
	}

	public void testUserModel() throws Exception {
		List<String> users = GitBlit.self().getAllUsernames();
		assertTrue("No users found!", users.size() > 0);
		assertTrue("Admin not found", users.contains("admin"));
		UserModel model = GitBlit.self().getUserModel("admin");
		assertTrue(model.toString().equals("admin"));
		assertTrue("Admin missing #admin role!", model.canAdmin);
		model.canAdmin = false;
		assertFalse("Admin should not have #admin!", model.canAdmin);
		String repository = GitBlitSuite.getHelloworldRepository().getDirectory().getName();
		RepositoryModel repositoryModel = GitBlit.self().getRepositoryModel(model, repository);
		assertFalse("Admin can still access repository!", model.canAccessRepository(repositoryModel));
		model.addRepository(repository);
		assertTrue("Admin can't access repository!", model.canAccessRepository(repositoryModel));
		assertEquals(GitBlit.self().getRepositoryModel(model, "pretend"), null);
		assertNotNull(GitBlit.self().getRepositoryModel(model, repository));
		assertTrue(GitBlit.self().getRepositoryModels(model).size() > 0);
	}

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

		assertTrue(AccessRestrictionType.fromName("none").equals(AccessRestrictionType.NONE));
		assertTrue(AccessRestrictionType.fromName("push").equals(AccessRestrictionType.PUSH));
		assertTrue(AccessRestrictionType.fromName("clone").equals(AccessRestrictionType.CLONE));
		assertTrue(AccessRestrictionType.fromName("view").equals(AccessRestrictionType.VIEW));
	}

	public void testFileSettings() throws Exception {
		FileSettings settings = new FileSettings("distrib/gitblit.properties");
		assertTrue(settings.getBoolean("missing", true));
		assertTrue(settings.getString("missing", "default").equals("default"));
		assertTrue(settings.getInteger("missing", 10) == 10);
		assertTrue(settings.getInteger("realm.realmFile", 5) == 5);

		assertTrue(settings.getBoolean("git.enableGitServlet", false));
		assertTrue(settings.getString("realm.userService", null).equals("users.properties"));
		assertTrue(settings.getInteger("realm.minPasswordLength", 0) == 5);
		List<String> mdExtensions = settings.getStrings("web.markdownExtensions");
		assertTrue(mdExtensions.size() > 0);
		assertTrue(mdExtensions.contains("md"));

		List<String> keys = settings.getAllKeys("server");
		assertTrue(keys.size() > 0);
		assertTrue(keys.contains("server.httpsPort"));

		assertTrue(settings.getChar("web.forwardSlashCharacter", ' ') == '/');
	}

	public void testGitblitSettings() throws Exception {
		// These are already tested by above test method.
		assertTrue(GitBlit.getBoolean("missing", true));
		assertEquals("default", GitBlit.getString("missing", "default"));
		assertEquals(10, GitBlit.getInteger("missing", 10));
		assertEquals(5, GitBlit.getInteger("realm.userService", 5));

		assertTrue(GitBlit.getBoolean("git.enableGitServlet", false));
		assertEquals("distrib/users.properties", GitBlit.getString("realm.userService", null));
		assertEquals(5, GitBlit.getInteger("realm.minPasswordLength", 0));
		List<String> mdExtensions = GitBlit.getStrings("web.markdownExtensions");
		assertTrue(mdExtensions.size() > 0);
		assertTrue(mdExtensions.contains("md"));

		List<String> keys = GitBlit.getAllKeys("server");
		assertTrue(keys.size() > 0);
		assertTrue(keys.contains("server.httpsPort"));

		assertTrue(GitBlit.getChar("web.forwardSlashCharacter", ' ') == '/');
		assertFalse(GitBlit.isDebugMode());
	}

	public void testAuthentication() throws Exception {
		assertTrue(GitBlit.self().authenticate("admin", "admin".toCharArray()) != null);
	}

	public void testRepositories() throws Exception {
		assertTrue(GitBlit.self().getRepository("missing") == null);
		assertTrue(GitBlit.self().getRepositoryModel("missing") == null);
	}
}
