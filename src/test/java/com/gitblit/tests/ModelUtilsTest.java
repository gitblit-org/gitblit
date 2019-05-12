package com.gitblit.tests;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Test;

import com.gitblit.Constants;
import com.gitblit.utils.ModelUtils;

public class ModelUtilsTest {

	@After
	public void resetPrefix()
	{
		ModelUtils.setUserRepoPrefix(null);
	}


	@Test
	public void testGetUserRepoPrefix()
	{
		assertEquals(Constants.DEFAULT_USER_REPOSITORY_PREFIX, ModelUtils.getUserRepoPrefix());
	}


	@Test
	public void testSetUserRepoPrefix()
	{

		assertEquals(Constants.DEFAULT_USER_REPOSITORY_PREFIX, ModelUtils.getUserRepoPrefix());

		ModelUtils.setUserRepoPrefix("@");
		assertEquals("@", ModelUtils.getUserRepoPrefix());

		ModelUtils.setUserRepoPrefix("");
		assertEquals(Constants.DEFAULT_USER_REPOSITORY_PREFIX, ModelUtils.getUserRepoPrefix());

		ModelUtils.setUserRepoPrefix("user/");
		assertEquals("user/", ModelUtils.getUserRepoPrefix());

		ModelUtils.setUserRepoPrefix("u_");
		assertEquals("u_", ModelUtils.getUserRepoPrefix());

		ModelUtils.setUserRepoPrefix(null);
		assertEquals(Constants.DEFAULT_USER_REPOSITORY_PREFIX, ModelUtils.getUserRepoPrefix());

		ModelUtils.setUserRepoPrefix("/somedir/otherdir/");
		assertEquals("somedir/otherdir/", ModelUtils.getUserRepoPrefix());
	}


	@Test
	public void testGetPersonalPath()
	{
		String username = "rob";
		assertEquals(Constants.DEFAULT_USER_REPOSITORY_PREFIX+username.toLowerCase(), ModelUtils.getPersonalPath(username));

		username = "James";
		assertEquals(Constants.DEFAULT_USER_REPOSITORY_PREFIX+username.toLowerCase(), ModelUtils.getPersonalPath(username));

		ModelUtils.setUserRepoPrefix("usr/");
		username = "noMan";
		assertEquals("usr/"+username.toLowerCase(), ModelUtils.getPersonalPath(username));
	}


	@Test
	public void testIsPersonalRepository()
	{
		String reponame = Constants.DEFAULT_USER_REPOSITORY_PREFIX + "one";
		assertTrue(ModelUtils.isPersonalRepository(reponame));

		reponame = "none";
		assertFalse(ModelUtils.isPersonalRepository(reponame));

		ModelUtils.setUserRepoPrefix("@@");
		reponame = "@@two";
		assertTrue(ModelUtils.isPersonalRepository(reponame));

		ModelUtils.setUserRepoPrefix("users/");
		reponame = "users/three";
		assertTrue(ModelUtils.isPersonalRepository(reponame));

		reponame = "project/four";
		assertFalse(ModelUtils.isPersonalRepository(reponame));
	}


	@Test
	public void testIsUsersPersonalRepository()
	{
		String reponame = Constants.DEFAULT_USER_REPOSITORY_PREFIX + "lynn";
		assertTrue(ModelUtils.isUsersPersonalRepository("lynn", reponame));

		reponame = "prjB";
		assertFalse(ModelUtils.isUsersPersonalRepository("lynn", reponame));

		ModelUtils.setUserRepoPrefix("@@");
		reponame = "@@newton";
		assertTrue(ModelUtils.isUsersPersonalRepository("newton", reponame));
		assertFalse(ModelUtils.isUsersPersonalRepository("hertz", reponame));

		ModelUtils.setUserRepoPrefix("users/");
		reponame = "users/fee";
		assertTrue(ModelUtils.isUsersPersonalRepository("fee", reponame));
		assertFalse(ModelUtils.isUsersPersonalRepository("gnome", reponame));

		reponame = "project/nsbl";
		assertFalse(ModelUtils.isUsersPersonalRepository("fee", reponame));
	}


	@Test
	public void testGetUserNameFromRepoPath()
	{
		String reponame = Constants.DEFAULT_USER_REPOSITORY_PREFIX + "lynn";
		assertEquals("lynn", ModelUtils.getUserNameFromRepoPath(reponame));

		ModelUtils.setUserRepoPrefix("@@");
		reponame = "@@newton";
		assertEquals("newton", ModelUtils.getUserNameFromRepoPath(reponame));

		ModelUtils.setUserRepoPrefix("users/");
		reponame = "users/fee";
		assertEquals("fee", ModelUtils.getUserNameFromRepoPath(reponame));
	}

}
