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
