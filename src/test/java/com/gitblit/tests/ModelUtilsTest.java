package com.gitblit.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Test;

import com.gitblit.Keys;
import com.gitblit.tests.mock.MemorySettings;
import com.gitblit.utils.ModelUtils;

public class ModelUtilsTest {

	private static final String DEFAULT_USER_REPO_PREFIX = "~";

	private static final Map<String, Object> backingMap = new HashMap<String, Object>();
	private static final MemorySettings ms = new MemorySettings(backingMap);


	private static void setPrefix(String prefix)
	{
		backingMap.put(Keys.git.userRepositoryPrefix, prefix);
	}


	private static void setRepoPrefix(String prefix)
	{
		backingMap.put(Keys.git.userRepositoryPrefix, prefix);
		ModelUtils.setUserRepoPrefix(ms);
	}


	@After
	public void resetPrefix()
	{
		setRepoPrefix(DEFAULT_USER_REPO_PREFIX);
	}


	@Test
	public void testGetUserRepoPrefix()
	{
		assertEquals(DEFAULT_USER_REPO_PREFIX, ModelUtils.getUserRepoPrefix());
	}


	@Test
	public void testSetUserRepoPrefix()
	{

		assertEquals(DEFAULT_USER_REPO_PREFIX, ModelUtils.getUserRepoPrefix());

		setPrefix("@");
		ModelUtils.setUserRepoPrefix(ms);
		assertEquals("@", ModelUtils.getUserRepoPrefix());

		backingMap.remove(Keys.git.userRepositoryPrefix);
		ModelUtils.setUserRepoPrefix(ms);
		assertEquals(DEFAULT_USER_REPO_PREFIX, ModelUtils.getUserRepoPrefix());

		setPrefix("user/");
		ModelUtils.setUserRepoPrefix(ms);
		assertEquals("user/", ModelUtils.getUserRepoPrefix());

		setPrefix("");
		ModelUtils.setUserRepoPrefix(ms);
		assertEquals(DEFAULT_USER_REPO_PREFIX, ModelUtils.getUserRepoPrefix());

		setPrefix("u_");
		ModelUtils.setUserRepoPrefix(ms);
		assertEquals("u_", ModelUtils.getUserRepoPrefix());

		ModelUtils.setUserRepoPrefix(null);
		assertEquals(DEFAULT_USER_REPO_PREFIX, ModelUtils.getUserRepoPrefix());

		setPrefix("/somedir/otherdir/");
		ModelUtils.setUserRepoPrefix(ms);
		assertEquals("somedir/otherdir/", ModelUtils.getUserRepoPrefix());

		setPrefix(DEFAULT_USER_REPO_PREFIX);
		ModelUtils.setUserRepoPrefix(ms);
		assertEquals(DEFAULT_USER_REPO_PREFIX, ModelUtils.getUserRepoPrefix());
	}


	@Test
	public void testGetPersonalPath()
	{
		String username = "rob";
		assertEquals(DEFAULT_USER_REPO_PREFIX+username.toLowerCase(), ModelUtils.getPersonalPath(username));

		username = "James";
		assertEquals(DEFAULT_USER_REPO_PREFIX+username.toLowerCase(), ModelUtils.getPersonalPath(username));
		
		setRepoPrefix("usr/");
		username = "noMan";
		assertEquals("usr/"+username.toLowerCase(), ModelUtils.getPersonalPath(username));		
	}


	@Test
	public void testIsPersonalRepository()
	{
		String reponame = DEFAULT_USER_REPO_PREFIX + "one";
		assertTrue(ModelUtils.isPersonalRepository(reponame));

		reponame = "none";
		assertFalse(ModelUtils.isPersonalRepository(reponame));

		setRepoPrefix("@@");
		reponame = "@@two";
		assertTrue(ModelUtils.isPersonalRepository(reponame));

		setRepoPrefix("users/");
		reponame = "users/three";
		assertTrue(ModelUtils.isPersonalRepository(reponame));

		reponame = "project/four";
		assertFalse(ModelUtils.isPersonalRepository(reponame));
	}


	@Test
	public void testIsUsersPersonalRepository()
	{
		String reponame = DEFAULT_USER_REPO_PREFIX + "lynn";
		assertTrue(ModelUtils.isUsersPersonalRepository("lynn", reponame));

		reponame = "prjB";
		assertFalse(ModelUtils.isUsersPersonalRepository("lynn", reponame));

		setRepoPrefix("@@");
		reponame = "@@newton";
		assertTrue(ModelUtils.isUsersPersonalRepository("newton", reponame));
		assertFalse(ModelUtils.isUsersPersonalRepository("hertz", reponame));

		setRepoPrefix("users/");
		reponame = "users/fee";
		assertTrue(ModelUtils.isUsersPersonalRepository("fee", reponame));
		assertFalse(ModelUtils.isUsersPersonalRepository("gnome", reponame));

		reponame = "project/nsbl";
		assertFalse(ModelUtils.isUsersPersonalRepository("fee", reponame));
	}


	@Test
	public void testGetUserNameFromRepoPath()
	{
		String reponame = DEFAULT_USER_REPO_PREFIX + "lynn";
		assertEquals("lynn", ModelUtils.getUserNameFromRepoPath(reponame));

		setRepoPrefix("@@");
		reponame = "@@newton";
		assertEquals("newton", ModelUtils.getUserNameFromRepoPath(reponame));

		setRepoPrefix("users/");
		reponame = "users/fee";
		assertEquals("fee", ModelUtils.getUserNameFromRepoPath(reponame));
	}

}
