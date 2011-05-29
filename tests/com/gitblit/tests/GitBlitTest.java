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

import com.gitblit.GitBlit;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;

public class GitBlitTest extends TestCase {

	public void testRepositoryModel() throws Exception {
		List<String> repositories = GitBlit.self().getRepositoryList();
		assertTrue("Repository list is empty!", repositories.size() > 0);
		assertTrue("Missing Helloworld repository!", repositories.contains(GitBlitSuite.getHelloworldRepository().getDirectory().getName()));
		RepositoryModel model = GitBlit.self().getRepositoryModel(GitBlitSuite.getHelloworldRepository().getDirectory().getName());
		assertTrue("Helloworld model is null!", model != null);
		assertTrue(model.toString().equals(GitBlitSuite.getHelloworldRepository().getDirectory().getName()));
	}
	
	public void testUserModel() throws Exception {
		List<String> users = GitBlit.self().getAllUsernames();
		assertTrue("No users found!", users.size() > 0);
		assertTrue("Admin not found", users.contains("admin"));
		UserModel model = GitBlit.self().getUserModel("admin");
		assertTrue(model.toString().equals("admin"));
		assertTrue("Admin missing #admin role!", model.canAdmin);
		model.canAdmin = false;
		assertFalse("Admin should not hae #admin!", model.canAdmin);
		String repository = GitBlitSuite.getHelloworldRepository().getDirectory().getName();
		assertFalse("Admin can still access repository!", model.canAccessRepository(repository));
		model.addRepository(repository);
		assertTrue("Admin can't access repository!", model.canAccessRepository(repository));
	}
}
