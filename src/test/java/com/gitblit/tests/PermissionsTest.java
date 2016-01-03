/*
 * Copyright 2012 gitblit.com.
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

import java.util.Date;

import org.junit.Test;

import com.gitblit.Constants.AccessPermission;
import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.Constants.AuthorizationControl;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.TeamModel;
import com.gitblit.models.UserModel;

/**
 * Comprehensive, brute-force test of all permutations of discrete permissions.
 *
 * @author James Moger
 *
 */
public class PermissionsTest extends GitblitUnitTest {

	/**
	 * Admin access rights/permissions
	 */
	@Test
	public void testAdmin() throws Exception {
		UserModel user = new UserModel("admin");
		user.canAdmin = true;

		for (AccessRestrictionType ar : AccessRestrictionType.values()) {
			RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
			repository.authorizationControl = AuthorizationControl.NAMED;
			repository.accessRestriction = ar;

			assertTrue("admin CAN NOT view!", user.canView(repository));
			assertTrue("admin CAN NOT clone!", user.canClone(repository));
			assertTrue("admin CAN NOT push!", user.canPush(repository));

			assertTrue("admin CAN NOT create ref!", user.canCreateRef(repository));
			assertTrue("admin CAN NOT delete ref!", user.canDeleteRef(repository));
			assertTrue("admin CAN NOT rewind ref!", user.canRewindRef(repository));

			assertEquals("admin has wrong permission!", AccessPermission.REWIND, user.getRepositoryPermission(repository).permission);

			assertTrue("admin CAN NOT fork!", user.canFork(repository));

			assertTrue("admin CAN NOT delete!", user.canDelete(repository));
			assertTrue("admin CAN NOT edit!", user.canEdit(repository));
		}
	}

	/**
	 * Anonymous access rights/permissions
	 */
	@Test
	public void testAnonymous_NONE() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.NONE;

		UserModel user = UserModel.ANONYMOUS;

		// all permissions, except fork
		assertTrue("anonymous CAN NOT view!", user.canView(repository));
		assertTrue("anonymous CAN NOT clone!", user.canClone(repository));
		assertTrue("anonymous CAN NOT push!", user.canPush(repository));

		assertTrue("anonymous CAN NOT create ref!", user.canCreateRef(repository));
		assertTrue("anonymous CAN NOT delete ref!", user.canDeleteRef(repository));
		assertTrue("anonymous CAN NOT rewind ref!", user.canRewindRef(repository));

		assertEquals("anonymous has wrong permission!", AccessPermission.REWIND, user.getRepositoryPermission(repository).permission);

		repository.allowForks = false;
		assertFalse("anonymous CAN fork!", user.canFork(repository));
		repository.allowForks = true;
		assertFalse("anonymous CAN fork!", user.canFork(repository));

		assertFalse("anonymous CAN delete!", user.canDelete(repository));
		assertFalse("anonymous CAN edit!", user.canEdit(repository));
	}

	@Test
	public void testAnonymous_PUSH() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.PUSH;

		UserModel user = UserModel.ANONYMOUS;

		assertTrue("anonymous CAN NOT view!", user.canView(repository));
		assertTrue("anonymous CAN NOT clone!", user.canClone(repository));
		assertFalse("anonymous CAN push!", user.canPush(repository));

		assertFalse("anonymous CAN create ref!", user.canCreateRef(repository));
		assertFalse("anonymous CAN delete ref!", user.canDeleteRef(repository));
		assertFalse("anonymous CAN rewind ref!", user.canRewindRef(repository));

		assertEquals("anonymous has wrong permission!", AccessPermission.CLONE, user.getRepositoryPermission(repository).permission);

		repository.allowForks = false;
		assertFalse("anonymous CAN fork!", user.canFork(repository));
		repository.allowForks = true;
		assertFalse("anonymous CAN fork!", user.canFork(repository));
	}

	@Test
	public void testAnonymous_CLONE() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.CLONE;

		UserModel user = UserModel.ANONYMOUS;

		assertTrue("anonymous CAN NOT view!", user.canView(repository));
		assertFalse("anonymous CAN clone!", user.canClone(repository));
		assertFalse("anonymous CAN push!", user.canPush(repository));

		assertFalse("anonymous CAN create ref!", user.canCreateRef(repository));
		assertFalse("anonymous CAN delete ref!", user.canDeleteRef(repository));
		assertFalse("anonymous CAN rewind ref!", user.canRewindRef(repository));

		assertEquals("anonymous has wrong permission!", AccessPermission.VIEW, user.getRepositoryPermission(repository).permission);

		repository.allowForks = false;
		assertFalse("anonymous CAN fork!", user.canFork(repository));
		repository.allowForks = true;
		assertFalse("anonymous CAN fork!", user.canFork(repository));
	}

	@Test
	public void testAnonymous_VIEW() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.VIEW;

		UserModel user = UserModel.ANONYMOUS;

		assertFalse("anonymous CAN view!", user.canView(repository));
		assertFalse("anonymous CAN clone!", user.canClone(repository));
		assertFalse("anonymous CAN push!", user.canPush(repository));

		assertFalse("anonymous CAN create ref!", user.canCreateRef(repository));
		assertFalse("anonymous CAN delete ref!", user.canDeleteRef(repository));
		assertFalse("anonymous CAN rewind ref!", user.canRewindRef(repository));

		assertEquals("anonymous has wrong permission!", AccessPermission.NONE, user.getRepositoryPermission(repository).permission);

		repository.allowForks = false;
		assertFalse("anonymous CAN fork!", user.canFork(repository));
		repository.allowForks = true;
		assertFalse("anonymous CAN fork!", user.canFork(repository));
	}

	/**
	 * Authenticated access rights/permissions
	 */
	@Test
	public void testAuthenticated_NONE() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.AUTHENTICATED;
		repository.accessRestriction = AccessRestrictionType.NONE;

		UserModel user = new UserModel("test");

		// all permissions, except fork
		assertTrue("authenticated CAN NOT view!", user.canView(repository));
		assertTrue("authenticated CAN NOT clone!", user.canClone(repository));
		assertTrue("authenticated CAN NOT push!", user.canPush(repository));

		assertTrue("authenticated CAN NOT create ref!", user.canCreateRef(repository));
		assertTrue("authenticated CAN NOT delete ref!", user.canDeleteRef(repository));
		assertTrue("authenticated CAN NOT rewind ref!", user.canRewindRef(repository));

		assertEquals("authenticated has wrong permission!", AccessPermission.REWIND, user.getRepositoryPermission(repository).permission);

		user.canFork = false;
		repository.allowForks = false;
		assertFalse("authenticated CAN fork!", user.canFork(repository));
		repository.allowForks = true;
		assertFalse("authenticated CAN fork!", user.canFork(repository));
		user.canFork = true;
		assertTrue("authenticated CAN NOT fork!", user.canFork(repository));

		assertFalse("authenticated CAN delete!", user.canDelete(repository));
		assertFalse("authenticated CAN edit!", user.canEdit(repository));
	}

	@Test
	public void testAuthenticated_PUSH() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.AUTHENTICATED;
		repository.accessRestriction = AccessRestrictionType.PUSH;

		UserModel user = new UserModel("test");

		assertTrue("authenticated CAN NOT view!", user.canView(repository));
		assertTrue("authenticated CAN NOT clone!", user.canClone(repository));
		assertTrue("authenticated CAN NOT push!", user.canPush(repository));

		assertTrue("authenticated CAN NOT create ref!", user.canCreateRef(repository));
		assertTrue("authenticated CAN NOT delete ref!", user.canDeleteRef(repository));
		assertTrue("authenticated CAN NOT rewind ref!", user.canRewindRef(repository));

		assertEquals("authenticated has wrong permission!", AccessPermission.REWIND, user.getRepositoryPermission(repository).permission);

		user.canFork = false;
		repository.allowForks = false;
		assertFalse("authenticated CAN fork!", user.canFork(repository));
		repository.allowForks = true;
		assertFalse("authenticated CAN fork!", user.canFork(repository));
		user.canFork = true;
		assertTrue("authenticated CAN NOT fork!", user.canFork(repository));
	}

	@Test
	public void testAuthenticated_CLONE() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.AUTHENTICATED;
		repository.accessRestriction = AccessRestrictionType.CLONE;

		UserModel user = new UserModel("test");

		assertTrue("authenticated CAN NOT view!", user.canView(repository));
		assertTrue("authenticated CAN NOT clone!", user.canClone(repository));
		assertTrue("authenticated CAN NOT push!", user.canPush(repository));

		assertTrue("authenticated CAN NOT create ref!", user.canCreateRef(repository));
		assertTrue("authenticated CAN NOT delete ref!", user.canDeleteRef(repository));
		assertTrue("authenticated CAN NOT rewind ref!", user.canRewindRef(repository));

		assertEquals("authenticated has wrong permission!", AccessPermission.REWIND, user.getRepositoryPermission(repository).permission);

		user.canFork = false;
		repository.allowForks = false;
		assertFalse("authenticated CAN fork!", user.canFork(repository));
		repository.allowForks = true;
		assertFalse("authenticated CAN fork!", user.canFork(repository));
		user.canFork = true;
		assertTrue("authenticated CAN NOT fork!", user.canFork(repository));
	}

	@Test
	public void testAuthenticated_VIEW() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.AUTHENTICATED;
		repository.accessRestriction = AccessRestrictionType.VIEW;

		UserModel user = new UserModel("test");

		assertTrue("authenticated CAN NOT view!", user.canView(repository));
		assertTrue("authenticated CAN NOT clone!", user.canClone(repository));
		assertTrue("authenticated CAN NOT push!", user.canPush(repository));

		assertTrue("authenticated CAN NOT create ref!", user.canCreateRef(repository));
		assertTrue("authenticated CAN NOT delete ref!", user.canDeleteRef(repository));
		assertTrue("authenticated CAN NOT rewind ref!", user.canRewindRef(repository));

		assertEquals("authenticated has wrong permission!", AccessPermission.REWIND, user.getRepositoryPermission(repository).permission);

		user.canFork = false;
		repository.allowForks = false;
		assertFalse("authenticated CAN fork!", user.canFork(repository));
		repository.allowForks = true;
		assertFalse("authenticated CAN fork!", user.canFork(repository));
		user.canFork = true;
		assertTrue("authenticated CAN NOT fork!", user.canFork(repository));
	}

	/**
	 * NONE_NONE = NO access restriction, NO access permission
	 */
	@Test
	public void testNamed_NONE_NONE() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.NONE;

		UserModel user = new UserModel("test");

		assertTrue("named CAN NOT view!", user.canView(repository));
		assertTrue("named CAN NOT clone!", user.canClone(repository));
		assertTrue("named CAN NOT push!", user.canPush(repository));

		assertTrue("named CAN NOT create ref!", user.canCreateRef(repository));
		assertTrue("named CAN NOT delete ref!", user.canDeleteRef(repository));
		assertTrue("named CAN NOT rewind ref!", user.canRewindRef(repository));

		assertEquals("named has wrong permission!", AccessPermission.REWIND, user.getRepositoryPermission(repository).permission);

		repository.allowForks = false;
		user.canFork = false;
		assertFalse("named CAN fork!", user.canFork(repository));
		user.canFork = true;
		assertFalse("named CAN fork!", user.canFork(repository));
		repository.allowForks = true;
		assertTrue("named CAN NOT fork!", user.canFork(repository));

		assertFalse("named CAN delete!", user.canDelete(repository));
		assertFalse("named CAN edit!", user.canEdit(repository));
	}

	/**
	 * PUSH_NONE = PUSH access restriction, NO access permission
	 */
	@Test
	public void testNamed_PUSH_NONE() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.PUSH;

		UserModel user = new UserModel("test");

		assertTrue("named CAN NOT view!", user.canView(repository));
		assertTrue("named CAN NOT clone!", user.canClone(repository));
		assertFalse("named CAN push!", user.canPush(repository));

		assertFalse("named CAN create ref!", user.canCreateRef(repository));
		assertFalse("named CAN delete ref!", user.canDeleteRef(repository));
		assertFalse("named CAN rewind ref!", user.canRewindRef(repository));

		assertEquals("named has wrong permission!", AccessPermission.CLONE, user.getRepositoryPermission(repository).permission);

		repository.allowForks = false;
		user.canFork = false;
		assertFalse("named CAN fork!", user.canFork(repository));
		user.canFork = true;
		assertFalse("named CAN fork!", user.canFork(repository));
		repository.allowForks = true;
		assertTrue("named CAN NOT fork!", user.canFork(repository));
	}

	/**
	 * CLONE_NONE = CLONE access restriction, NO access permission
	 */
	@Test
	public void testNamed_CLONE_NONE() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.CLONE;

		UserModel user = new UserModel("test");

		assertTrue("named CAN NOT view!", user.canView(repository));
		assertFalse("named CAN clone!", user.canClone(repository));
		assertFalse("named CAN push!", user.canPush(repository));

		assertFalse("named CAN create ref!", user.canCreateRef(repository));
		assertFalse("named CAN delete ref!", user.canDeleteRef(repository));
		assertFalse("named CAN rewind ref!", user.canRewindRef(repository));

		assertEquals("named has wrong permission!", AccessPermission.VIEW, user.getRepositoryPermission(repository).permission);

		repository.allowForks = false;
		user.canFork = false;
		assertFalse("named CAN fork!", user.canFork(repository));
		user.canFork = true;
		assertFalse("named CAN fork!", user.canFork(repository));
		repository.allowForks = true;
		assertFalse("named CAN NOT fork!", user.canFork(repository));
	}

	/**
	 * VIEW_NONE = VIEW access restriction, NO access permission
	 */
	@Test
	public void testNamed_VIEW_NONE() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.VIEW;

		UserModel user = new UserModel("test");

		assertFalse("named CAN view!", user.canView(repository));
		assertFalse("named CAN clone!", user.canClone(repository));
		assertFalse("named CAN push!", user.canPush(repository));

		assertFalse("named CAN create ref!", user.canCreateRef(repository));
		assertFalse("named CAN delete ref!", user.canDeleteRef(repository));
		assertFalse("named CAN rewind ref!", user.canRewindRef(repository));

		assertEquals("named has wrong permission!", AccessPermission.NONE, user.getRepositoryPermission(repository).permission);

		repository.allowForks = false;
		user.canFork = false;
		assertFalse("named CAN fork!", user.canFork(repository));
		user.canFork = true;
		assertFalse("named CAN fork!", user.canFork(repository));
		repository.allowForks = true;
		assertFalse("named CAN NOT fork!", user.canFork(repository));
	}


	/**
	 * NONE_VIEW = NO access restriction, VIEW access permission.
	 * (not useful scenario)
	 */
	@Test
	public void testNamed_NONE_VIEW() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.NONE;

		UserModel user = new UserModel("test");
		user.setRepositoryPermission(repository.name, AccessPermission.VIEW);

		assertTrue("named CAN NOT view!", user.canView(repository));
		assertTrue("named CAN NOT clone!", user.canClone(repository));
		assertTrue("named CAN NOT push!", user.canPush(repository));

		assertTrue("named CAN NOT create ref!", user.canCreateRef(repository));
		assertTrue("named CAN NOT delete ref!", user.canDeleteRef(repository));
		assertTrue("named CAN NOT rewind ref!", user.canRewindRef(repository));

		assertEquals("named has wrong permission!", AccessPermission.REWIND, user.getRepositoryPermission(repository).permission);

		repository.allowForks = false;
		user.canFork = false;
		assertFalse("named CAN fork!", user.canFork(repository));
		user.canFork = true;
		assertFalse("named CAN fork!", user.canFork(repository));
		repository.allowForks = true;
		assertTrue("named CAN NOT fork!", user.canFork(repository));
	}

	/**
	 * PUSH_VIEW = PUSH access restriction, VIEW access permission
	 */
	@Test
	public void testNamed_PUSH_VIEW() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.PUSH;

		UserModel user = new UserModel("test");
		user.setRepositoryPermission(repository.name, AccessPermission.VIEW);

		assertTrue("named CAN NOT view!", user.canView(repository));
		assertTrue("named CAN NOT clone!", user.canClone(repository));
		assertFalse("named CAN push!", user.canPush(repository));

		assertFalse("named CAN create ref!", user.canCreateRef(repository));
		assertFalse("named CAN delete ref!", user.canDeleteRef(repository));
		assertFalse("named CAN rewind ref!", user.canRewindRef(repository));

		assertEquals("named has wrong permission!", AccessPermission.CLONE, user.getRepositoryPermission(repository).permission);

		repository.allowForks = false;
		user.canFork = false;
		assertFalse("named CAN fork!", user.canFork(repository));
		user.canFork = true;
		assertFalse("named CAN fork!", user.canFork(repository));
		repository.allowForks = true;
		assertTrue("named CAN NOT fork!", user.canFork(repository));
	}

	/**
	 * CLONE_VIEW = CLONE access restriction, VIEW access permission
	 */
	@Test
	public void testNamed_CLONE_VIEW() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.CLONE;

		UserModel user = new UserModel("test");
		user.setRepositoryPermission(repository.name, AccessPermission.VIEW);

		assertTrue("named CAN NOT view!", user.canView(repository));
		assertFalse("named CAN clone!", user.canClone(repository));
		assertFalse("named CAN push!", user.canPush(repository));

		assertFalse("named CAN create ref!", user.canCreateRef(repository));
		assertFalse("named CAN delete ref!", user.canDeleteRef(repository));
		assertFalse("named CAN rewind ref!", user.canRewindRef(repository));

		assertEquals("named has wrong permission!", AccessPermission.VIEW, user.getRepositoryPermission(repository).permission);

		repository.allowForks = false;
		user.canFork = false;
		assertFalse("named CAN fork!", user.canFork(repository));
		user.canFork = true;
		assertFalse("named CAN fork!", user.canFork(repository));
		repository.allowForks = true;
		assertFalse("named CAN NOT fork!", user.canFork(repository));
	}

	/**
	 * VIEW_VIEW = VIEW access restriction, VIEW access permission
	 */
	@Test
	public void testNamed_VIEW_VIEW() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.VIEW;

		UserModel user = new UserModel("test");
		user.setRepositoryPermission(repository.name, AccessPermission.VIEW);

		assertTrue("named CAN NOT view!", user.canView(repository));
		assertFalse("named CAN clone!", user.canClone(repository));
		assertFalse("named CAN push!", user.canPush(repository));

		assertFalse("named CAN create ref!", user.canCreateRef(repository));
		assertFalse("named CAN delete ref!", user.canDeleteRef(repository));
		assertFalse("named CAN rewind ref!", user.canRewindRef(repository));

		assertEquals("named has wrong permission!", AccessPermission.VIEW, user.getRepositoryPermission(repository).permission);

		repository.allowForks = false;
		user.canFork = false;
		assertFalse("named CAN fork!", user.canFork(repository));
		user.canFork = true;
		assertFalse("named CAN fork!", user.canFork(repository));
		repository.allowForks = true;
		assertFalse("named CAN NOT fork!", user.canFork(repository));
	}

	/**
	 * NONE_CLONE = NO access restriction, CLONE access permission.
	 * (not useful scenario)
	 */
	@Test
	public void testNamed_NONE_CLONE() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.NONE;

		UserModel user = new UserModel("test");
		user.setRepositoryPermission(repository.name, AccessPermission.CLONE);

		assertTrue("named CAN NOT view!", user.canView(repository));
		assertTrue("named CAN NOT clone!", user.canClone(repository));
		assertTrue("named CAN NOT push!", user.canPush(repository));

		assertTrue("named CAN NOT create ref!", user.canCreateRef(repository));
		assertTrue("named CAN NOT delete ref!", user.canDeleteRef(repository));
		assertTrue("named CAN NOT rewind ref!", user.canRewindRef(repository));

		assertEquals("named has wrong permission!", AccessPermission.REWIND, user.getRepositoryPermission(repository).permission);

		repository.allowForks = false;
		user.canFork = false;
		assertFalse("named CAN fork!", user.canFork(repository));
		user.canFork = true;
		assertFalse("named CAN fork!", user.canFork(repository));
		repository.allowForks = true;
		assertTrue("named CAN NOT fork!", user.canFork(repository));
	}

	/**
	 * PUSH_CLONE = PUSH access restriction, CLONE access permission
	 */
	@Test
	public void testNamed_PUSH_CLONE() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.PUSH;

		UserModel user = new UserModel("test");
		user.setRepositoryPermission(repository.name, AccessPermission.CLONE);

		assertTrue("named CAN NOT view!", user.canView(repository));
		assertTrue("named CAN NOT clone!", user.canClone(repository));
		assertFalse("named CAN push!", user.canPush(repository));

		assertFalse("named CAN create ref!", user.canCreateRef(repository));
		assertFalse("named CAN delete ref!", user.canDeleteRef(repository));
		assertFalse("named CAN rewind ref!", user.canRewindRef(repository));

		assertEquals("named has wrong permission!", AccessPermission.CLONE, user.getRepositoryPermission(repository).permission);

		repository.allowForks = false;
		user.canFork = false;
		assertFalse("named CAN fork!", user.canFork(repository));
		user.canFork = true;
		assertFalse("named CAN fork!", user.canFork(repository));
		repository.allowForks = true;
		assertTrue("named CAN NOT fork!", user.canFork(repository));
	}

	/**
	 * CLONE_CLONE = CLONE access restriction, CLONE access permission
	 */
	@Test
	public void testNamed_CLONE_CLONE() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.CLONE;

		UserModel user = new UserModel("test");
		user.setRepositoryPermission(repository.name, AccessPermission.CLONE);

		assertTrue("named CAN NOT view!", user.canView(repository));
		assertTrue("named CAN NOT clone!", user.canClone(repository));
		assertFalse("named CAN push!", user.canPush(repository));

		assertFalse("named CAN create ref!", user.canCreateRef(repository));
		assertFalse("named CAN delete ref!", user.canDeleteRef(repository));
		assertFalse("named CAN rewind ref!", user.canRewindRef(repository));

		assertEquals("named has wrong permission!", AccessPermission.CLONE, user.getRepositoryPermission(repository).permission);

		repository.allowForks = false;
		user.canFork = false;
		assertFalse("named CAN fork!", user.canFork(repository));
		user.canFork = true;
		assertFalse("named CAN fork!", user.canFork(repository));
		repository.allowForks = true;
		assertTrue("named CAN NOT fork!", user.canFork(repository));
	}

	/**
	 * VIEW_CLONE = VIEW access restriction, CLONE access permission
	 */
	@Test
	public void testNamed_VIEW_CLONE() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.VIEW;

		UserModel user = new UserModel("test");
		user.setRepositoryPermission(repository.name, AccessPermission.CLONE);

		assertTrue("named CAN NOT view!", user.canView(repository));
		assertTrue("named CAN NOT clone!", user.canClone(repository));
		assertFalse("named CAN push!", user.canPush(repository));

		assertFalse("named CAN create ref!", user.canCreateRef(repository));
		assertFalse("named CAN delete ref!", user.canDeleteRef(repository));
		assertFalse("named CAN rewind ref!", user.canRewindRef(repository));

		assertEquals("named has wrong permission!", AccessPermission.CLONE, user.getRepositoryPermission(repository).permission);

		repository.allowForks = false;
		user.canFork = false;
		assertFalse("named CAN fork!", user.canFork(repository));
		user.canFork = true;
		assertFalse("named CAN fork!", user.canFork(repository));
		repository.allowForks = true;
		assertTrue("named CAN NOT fork!", user.canFork(repository));
	}

	/**
	 * NONE_PUSH = NO access restriction, PUSH access permission.
	 * (not useful scenario)
	 */
	@Test
	public void testNamed_NONE_PUSH() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.NONE;

		UserModel user = new UserModel("test");
		user.setRepositoryPermission(repository.name, AccessPermission.PUSH);

		assertTrue("named CAN NOT view!", user.canView(repository));
		assertTrue("named CAN NOT clone!", user.canClone(repository));
		assertTrue("named CAN NOT push!", user.canPush(repository));

		assertTrue("named CAN NOT create ref!", user.canCreateRef(repository));
		assertTrue("named CAN NOT delete ref!", user.canDeleteRef(repository));
		assertTrue("named CAN NOT rewind ref!", user.canRewindRef(repository));

		assertEquals("named has wrong permission!", AccessPermission.REWIND, user.getRepositoryPermission(repository).permission);

		repository.allowForks = false;
		user.canFork = false;
		assertFalse("named CAN fork!", user.canFork(repository));
		user.canFork = true;
		assertFalse("named CAN fork!", user.canFork(repository));
		repository.allowForks = true;
		assertTrue("named CAN NOT fork!", user.canFork(repository));
	}

	/**
	 * PUSH_PUSH = PUSH access restriction, PUSH access permission
	 */
	@Test
	public void testNamed_PUSH_PUSH() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.PUSH;

		UserModel user = new UserModel("test");
		user.setRepositoryPermission(repository.name, AccessPermission.PUSH);

		assertTrue("named CAN NOT view!", user.canView(repository));
		assertTrue("named CAN NOT clone!", user.canClone(repository));
		assertTrue("named CAN NOT push!", user.canPush(repository));

		assertFalse("named CAN create ref!", user.canCreateRef(repository));
		assertFalse("named CAN delete ref!", user.canDeleteRef(repository));
		assertFalse("named CAN rewind ref!", user.canRewindRef(repository));

		assertEquals("named has wrong permission!", AccessPermission.PUSH, user.getRepositoryPermission(repository).permission);

		repository.allowForks = false;
		user.canFork = false;
		assertFalse("named CAN fork!", user.canFork(repository));
		user.canFork = true;
		assertFalse("named CAN fork!", user.canFork(repository));
		repository.allowForks = true;
		assertTrue("named CAN NOT fork!", user.canFork(repository));
	}

	/**
	 * CLONE_PUSH = CLONE access restriction, PUSH access permission
	 */
	@Test
	public void testNamed_CLONE_PUSH() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.CLONE;

		UserModel user = new UserModel("test");
		user.setRepositoryPermission(repository.name, AccessPermission.PUSH);

		assertTrue("named CAN NOT view!", user.canView(repository));
		assertTrue("named CAN NOT clone!", user.canClone(repository));
		assertTrue("named CAN NOT push!", user.canPush(repository));

		assertFalse("named CAN create ref!", user.canCreateRef(repository));
		assertFalse("named CAN delete red!", user.canDeleteRef(repository));
		assertFalse("named CAN rewind ref!", user.canRewindRef(repository));

		assertEquals("named has wrong permission!", AccessPermission.PUSH, user.getRepositoryPermission(repository).permission);

		repository.allowForks = false;
		user.canFork = false;
		assertFalse("named CAN fork!", user.canFork(repository));
		user.canFork = true;
		assertFalse("named CAN fork!", user.canFork(repository));
		repository.allowForks = true;
		assertTrue("named CAN NOT fork!", user.canFork(repository));
	}

	/**
	 * VIEW_PUSH = VIEW access restriction, PUSH access permission
	 */
	@Test
	public void testNamed_VIEW_PUSH() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.VIEW;

		UserModel user = new UserModel("test");
		user.setRepositoryPermission(repository.name, AccessPermission.PUSH);

		assertTrue("named CAN NOT view!", user.canView(repository));
		assertTrue("named CAN NOT clone!", user.canClone(repository));
		assertTrue("named CAN not push!", user.canPush(repository));

		assertFalse("named CAN create ref!", user.canCreateRef(repository));
		assertFalse("named CAN delete ref!", user.canDeleteRef(repository));
		assertFalse("named CAN rewind ref!", user.canRewindRef(repository));

		assertEquals("named has wrong permission!", AccessPermission.PUSH, user.getRepositoryPermission(repository).permission);

		repository.allowForks = false;
		user.canFork = false;
		assertFalse("named CAN fork!", user.canFork(repository));
		user.canFork = true;
		assertFalse("named CAN fork!", user.canFork(repository));
		repository.allowForks = true;
		assertTrue("named CAN NOT fork!", user.canFork(repository));
	}

	/**
	 * NONE_CREATE = NO access restriction, CREATE access permission.
	 * (not useful scenario)
	 */
	@Test
	public void testNamed_NONE_CREATE() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.NONE;

		UserModel user = new UserModel("test");
		user.setRepositoryPermission(repository.name, AccessPermission.CREATE);

		assertTrue("named CAN NOT view!", user.canView(repository));
		assertTrue("named CAN NOT clone!", user.canClone(repository));
		assertTrue("named CAN NOT push!", user.canPush(repository));

		assertTrue("named CAN NOT create ref!", user.canCreateRef(repository));
		assertTrue("named CAN NOT delete ref!", user.canDeleteRef(repository));
		assertTrue("named CAN NOT rewind ref!", user.canRewindRef(repository));

		assertEquals("named has wrong permission!", AccessPermission.REWIND, user.getRepositoryPermission(repository).permission);

		repository.allowForks = false;
		user.canFork = false;
		assertFalse("named CAN fork!", user.canFork(repository));
		user.canFork = true;
		assertFalse("named CAN fork!", user.canFork(repository));
		repository.allowForks = true;
		assertTrue("named CAN NOT fork!", user.canFork(repository));
	}

	/**
	 * PUSH_CREATE = PUSH access restriction, CREATE access permission
	 */
	@Test
	public void testNamed_PUSH_CREATE() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.PUSH;

		UserModel user = new UserModel("test");
		user.setRepositoryPermission(repository.name, AccessPermission.CREATE);

		assertTrue("named CAN NOT view!", user.canView(repository));
		assertTrue("named CAN NOT clone!", user.canClone(repository));
		assertTrue("named CAN NOT push!", user.canPush(repository));

		assertTrue("named CAN NOT create ref!", user.canCreateRef(repository));
		assertFalse("named CAN delete ref!", user.canDeleteRef(repository));
		assertFalse("named CAN rewind ref!", user.canRewindRef(repository));

		assertEquals("named has wrong permission!", AccessPermission.CREATE, user.getRepositoryPermission(repository).permission);

		repository.allowForks = false;
		user.canFork = false;
		assertFalse("named CAN fork!", user.canFork(repository));
		user.canFork = true;
		assertFalse("named CAN fork!", user.canFork(repository));
		repository.allowForks = true;
		assertTrue("named CAN NOT fork!", user.canFork(repository));
	}

	/**
	 * CLONE_CREATE = CLONE access restriction, CREATE access permission
	 */
	@Test
	public void testNamed_CLONE_CREATE() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.CLONE;

		UserModel user = new UserModel("test");
		user.setRepositoryPermission(repository.name, AccessPermission.CREATE);

		assertTrue("named CAN NOT view!", user.canView(repository));
		assertTrue("named CAN NOT clone!", user.canClone(repository));
		assertTrue("named CAN NOT push!", user.canPush(repository));

		assertTrue("named CAN NOT create ref!", user.canCreateRef(repository));
		assertFalse("named CAN delete red!", user.canDeleteRef(repository));
		assertFalse("named CAN rewind ref!", user.canRewindRef(repository));

		assertEquals("named has wrong permission!", AccessPermission.CREATE, user.getRepositoryPermission(repository).permission);

		repository.allowForks = false;
		user.canFork = false;
		assertFalse("named CAN fork!", user.canFork(repository));
		user.canFork = true;
		assertFalse("named CAN fork!", user.canFork(repository));
		repository.allowForks = true;
		assertTrue("named CAN NOT fork!", user.canFork(repository));
	}

	/**
	 * VIEW_CREATE = VIEW access restriction, CREATE access permission
	 */
	@Test
	public void testNamed_VIEW_CREATE() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.VIEW;

		UserModel user = new UserModel("test");
		user.setRepositoryPermission(repository.name, AccessPermission.CREATE);

		assertTrue("named CAN NOT view!", user.canView(repository));
		assertTrue("named CAN NOT clone!", user.canClone(repository));
		assertTrue("named CAN not push!", user.canPush(repository));

		assertTrue("named CAN NOT create ref!", user.canCreateRef(repository));
		assertFalse("named CAN delete ref!", user.canDeleteRef(repository));
		assertFalse("named CAN rewind ref!", user.canRewindRef(repository));

		assertEquals("named has wrong permission!", AccessPermission.CREATE, user.getRepositoryPermission(repository).permission);

		repository.allowForks = false;
		user.canFork = false;
		assertFalse("named CAN fork!", user.canFork(repository));
		user.canFork = true;
		assertFalse("named CAN fork!", user.canFork(repository));
		repository.allowForks = true;
		assertTrue("named CAN NOT fork!", user.canFork(repository));
	}

	/**
	 * NONE_DELETE = NO access restriction, DELETE access permission.
	 * (not useful scenario)
	 */
	@Test
	public void testNamed_NONE_DELETE() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.NONE;

		UserModel user = new UserModel("test");
		user.setRepositoryPermission(repository.name, AccessPermission.DELETE);

		assertTrue("named CAN NOT view!", user.canView(repository));
		assertTrue("named CAN NOT clone!", user.canClone(repository));
		assertTrue("named CAN NOT push!", user.canPush(repository));

		assertTrue("named CAN NOT create ref!", user.canCreateRef(repository));
		assertTrue("named CAN NOT delete ref!", user.canDeleteRef(repository));
		assertTrue("named CAN NOT rewind ref!", user.canRewindRef(repository));

		assertEquals("named has wrong permission!", AccessPermission.REWIND, user.getRepositoryPermission(repository).permission);

		repository.allowForks = false;
		user.canFork = false;
		assertFalse("named CAN fork!", user.canFork(repository));
		user.canFork = true;
		assertFalse("named CAN fork!", user.canFork(repository));
		repository.allowForks = true;
		assertTrue("named CAN NOT fork!", user.canFork(repository));
	}

	/**
	 * PUSH_DELETE = PUSH access restriction, DELETE access permission
	 */
	@Test
	public void testNamed_PUSH_DELETE() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.PUSH;

		UserModel user = new UserModel("test");
		user.setRepositoryPermission(repository.name, AccessPermission.DELETE);

		assertTrue("named CAN NOT view!", user.canView(repository));
		assertTrue("named CAN NOT clone!", user.canClone(repository));
		assertTrue("named CAN NOT push!", user.canPush(repository));

		assertTrue("named CAN NOT create ref!", user.canCreateRef(repository));
		assertTrue("named CAN NOT delete ref!", user.canDeleteRef(repository));
		assertFalse("named CAN rewind ref!", user.canRewindRef(repository));

		assertEquals("named has wrong permission!", AccessPermission.DELETE, user.getRepositoryPermission(repository).permission);

		repository.allowForks = false;
		user.canFork = false;
		assertFalse("named CAN fork!", user.canFork(repository));
		user.canFork = true;
		assertFalse("named CAN fork!", user.canFork(repository));
		repository.allowForks = true;
		assertTrue("named CAN NOT fork!", user.canFork(repository));
	}

	/**
	 * CLONE_DELETE = CLONE access restriction, DELETE access permission
	 */
	@Test
	public void testNamed_CLONE_DELETE() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.CLONE;

		UserModel user = new UserModel("test");
		user.setRepositoryPermission(repository.name, AccessPermission.DELETE);

		assertTrue("named CAN NOT view!", user.canView(repository));
		assertTrue("named CAN NOT clone!", user.canClone(repository));
		assertTrue("named CAN NOT push!", user.canPush(repository));

		assertTrue("named CAN NOT create ref!", user.canCreateRef(repository));
		assertTrue("named CAN NOT delete red!", user.canDeleteRef(repository));
		assertFalse("named CAN rewind ref!", user.canRewindRef(repository));

		assertEquals("named has wrong permission!", AccessPermission.DELETE, user.getRepositoryPermission(repository).permission);

		repository.allowForks = false;
		user.canFork = false;
		assertFalse("named CAN fork!", user.canFork(repository));
		user.canFork = true;
		assertFalse("named CAN fork!", user.canFork(repository));
		repository.allowForks = true;
		assertTrue("named CAN NOT fork!", user.canFork(repository));
	}

	/**
	 * VIEW_DELETE = VIEW access restriction, DELETE access permission
	 */
	@Test
	public void testNamed_VIEW_DELETE() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.VIEW;

		UserModel user = new UserModel("test");
		user.setRepositoryPermission(repository.name, AccessPermission.DELETE);

		assertTrue("named CAN NOT view!", user.canView(repository));
		assertTrue("named CAN NOT clone!", user.canClone(repository));
		assertTrue("named CAN not push!", user.canPush(repository));

		assertTrue("named CAN NOT create ref!", user.canCreateRef(repository));
		assertTrue("named CAN NOT delete ref!", user.canDeleteRef(repository));
		assertFalse("named CAN rewind ref!", user.canRewindRef(repository));

		assertEquals("named has wrong permission!", AccessPermission.DELETE, user.getRepositoryPermission(repository).permission);

		repository.allowForks = false;
		user.canFork = false;
		assertFalse("named CAN fork!", user.canFork(repository));
		user.canFork = true;
		assertFalse("named CAN fork!", user.canFork(repository));
		repository.allowForks = true;
		assertTrue("named CAN NOT fork!", user.canFork(repository));
	}

	/**
	 * NONE_REWIND = NO access restriction, REWIND access permission.
	 * (not useful scenario)
	 */
	@Test
	public void testNamed_NONE_REWIND() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.NONE;

		UserModel user = new UserModel("test");
		user.setRepositoryPermission(repository.name, AccessPermission.REWIND);

		assertTrue("named CAN NOT view!", user.canView(repository));
		assertTrue("named CAN NOT clone!", user.canClone(repository));
		assertTrue("named CAN NOT push!", user.canPush(repository));

		assertTrue("named CAN NOT create ref!", user.canCreateRef(repository));
		assertTrue("named CAN NOT delete ref!", user.canDeleteRef(repository));
		assertTrue("named CAN NOT rewind ref!", user.canRewindRef(repository));

		assertEquals("named has wrong permission!", AccessPermission.REWIND, user.getRepositoryPermission(repository).permission);

		repository.allowForks = false;
		user.canFork = false;
		assertFalse("named CAN fork!", user.canFork(repository));
		user.canFork = true;
		assertFalse("named CAN fork!", user.canFork(repository));
		repository.allowForks = true;
		assertTrue("named CAN NOT fork!", user.canFork(repository));
	}

	/**
	 * PUSH_REWIND = PUSH access restriction, REWIND access permission
	 */
	@Test
	public void testNamed_PUSH_REWIND() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.PUSH;

		UserModel user = new UserModel("test");
		user.setRepositoryPermission(repository.name, AccessPermission.REWIND);

		assertTrue("named CAN NOT view!", user.canView(repository));
		assertTrue("named CAN NOT clone!", user.canClone(repository));
		assertTrue("named CAN NOT push!", user.canPush(repository));

		assertTrue("named CAN NOT create ref!", user.canCreateRef(repository));
		assertTrue("named CAN NOT delete ref!", user.canDeleteRef(repository));
		assertTrue("named CAN NOT rewind ref!", user.canRewindRef(repository));

		assertEquals("named has wrong permission!", AccessPermission.REWIND, user.getRepositoryPermission(repository).permission);

		repository.allowForks = false;
		user.canFork = false;
		assertFalse("named CAN fork!", user.canFork(repository));
		user.canFork = true;
		assertFalse("named CAN fork!", user.canFork(repository));
		repository.allowForks = true;
		assertTrue("named CAN NOT fork!", user.canFork(repository));
	}

	/**
	 * CLONE_REWIND = CLONE access restriction, REWIND access permission
	 */
	@Test
	public void testNamed_CLONE_REWIND() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.CLONE;

		UserModel user = new UserModel("test");
		user.setRepositoryPermission(repository.name, AccessPermission.REWIND);

		assertTrue("named CAN NOT view!", user.canView(repository));
		assertTrue("named CAN NOT clone!", user.canClone(repository));
		assertTrue("named CAN NOT push!", user.canPush(repository));

		assertTrue("named CAN NOT create ref!", user.canCreateRef(repository));
		assertTrue("named CAN NOT delete ref!", user.canDeleteRef(repository));
		assertTrue("named CAN NOT rewind ref!", user.canRewindRef(repository));

		assertEquals("named has wrong permission!", AccessPermission.REWIND, user.getRepositoryPermission(repository).permission);

		repository.allowForks = false;
		user.canFork = false;
		assertFalse("named CAN fork!", user.canFork(repository));
		user.canFork = true;
		assertFalse("named CAN fork!", user.canFork(repository));
		repository.allowForks = true;
		assertTrue("named CAN NOT fork!", user.canFork(repository));
	}

	/**
	 * VIEW_REWIND = VIEW access restriction, REWIND access permission
	 */
	@Test
	public void testNamed_VIEW_REWIND() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.VIEW;

		UserModel user = new UserModel("test");
		user.setRepositoryPermission(repository.name, AccessPermission.REWIND);

		assertTrue("named CAN NOT view!", user.canView(repository));
		assertTrue("named CAN NOT clone!", user.canClone(repository));
		assertTrue("named CAN NOT push!", user.canPush(repository));

		assertTrue("named CAN NOT create ref!", user.canCreateRef(repository));
		assertTrue("named CAN NOT delete ref!", user.canDeleteRef(repository));
		assertTrue("named CAN NOT rewind ref!", user.canRewindRef(repository));

		assertEquals("named has wrong permission!", AccessPermission.REWIND, user.getRepositoryPermission(repository).permission);

		repository.allowForks = false;
		user.canFork = false;
		assertFalse("named CAN fork!", user.canFork(repository));
		user.canFork = true;
		assertFalse("named CAN fork!", user.canFork(repository));
		repository.allowForks = true;
		assertTrue("named CAN NOT fork!", user.canFork(repository));
	}

	/**
	 * NONE_NONE = NO access restriction, NO access permission
	 */
	@Test
	public void testTeam_NONE_NONE() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.NONE;

		TeamModel team = new TeamModel("test");

		assertTrue("team CAN NOT view!", team.canView(repository));
		assertTrue("team CAN NOT clone!", team.canClone(repository));
		assertTrue("team CAN NOT push!", team.canPush(repository));

		assertTrue("team CAN NOT create ref!", team.canCreateRef(repository));
		assertTrue("team CAN NOT delete ref!", team.canDeleteRef(repository));
		assertTrue("team CAN NOT rewind ref!", team.canRewindRef(repository));

		assertEquals("team has wrong permission!", AccessPermission.REWIND, team.getRepositoryPermission(repository).permission);

	}

	/**
	 * PUSH_NONE = PUSH access restriction, NO access permission
	 */
	@Test
	public void testTeam_PUSH_NONE() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.PUSH;

		TeamModel team = new TeamModel("test");

		assertTrue("team CAN NOT view!", team.canView(repository));
		assertTrue("team CAN NOT clone!", team.canClone(repository));
		assertFalse("team CAN push!", team.canPush(repository));

		assertFalse("team CAN create ref!", team.canCreateRef(repository));
		assertFalse("team CAN delete ref!", team.canDeleteRef(repository));
		assertFalse("team CAN rewind ref!", team.canRewindRef(repository));

		assertEquals("team has wrong permission!", AccessPermission.CLONE, team.getRepositoryPermission(repository).permission);

	}

	/**
	 * CLONE_NONE = CLONE access restriction, NO access permission
	 */
	@Test
	public void testTeam_CLONE_NONE() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.CLONE;

		TeamModel team = new TeamModel("test");

		assertTrue("team CAN NOT view!", team.canView(repository));
		assertFalse("team CAN clone!", team.canClone(repository));
		assertFalse("team CAN push!", team.canPush(repository));

		assertFalse("team CAN create ref!", team.canCreateRef(repository));
		assertFalse("team CAN delete ref!", team.canDeleteRef(repository));
		assertFalse("team CAN rewind ref!", team.canRewindRef(repository));

		assertEquals("team has wrong permission!", AccessPermission.VIEW, team.getRepositoryPermission(repository).permission);
	}

	/**
	 * VIEW_NONE = VIEW access restriction, NO access permission
	 */
	@Test
	public void testTeam_VIEW_NONE() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.VIEW;

		TeamModel team = new TeamModel("test");

		assertFalse("team CAN view!", team.canView(repository));
		assertFalse("team CAN clone!", team.canClone(repository));
		assertFalse("team CAN push!", team.canPush(repository));

		assertFalse("team CAN create ref!", team.canCreateRef(repository));
		assertFalse("team CAN delete ref!", team.canDeleteRef(repository));
		assertFalse("team CAN rewind ref!", team.canRewindRef(repository));

		assertEquals("team has wrong permission!", AccessPermission.NONE, team.getRepositoryPermission(repository).permission);
	}

	/**
	 * NONE_PUSH = NO access restriction, PUSH access permission
	 * (not useful scenario)
	 */
	@Test
	public void testTeam_NONE_PUSH() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.NONE;

		TeamModel team = new TeamModel("test");
		team.setRepositoryPermission(repository.name, AccessPermission.PUSH);

		assertTrue("team CAN NOT view!", team.canView(repository));
		assertTrue("team CAN NOT clone!", team.canClone(repository));
		assertTrue("team CAN NOT push!", team.canPush(repository));

		assertTrue("team CAN NOT create ref!", team.canCreateRef(repository));
		assertTrue("team CAN NOT delete ref!", team.canDeleteRef(repository));
		assertTrue("team CAN NOT rewind ref!", team.canRewindRef(repository));

		assertEquals("team has wrong permission!", AccessPermission.REWIND, team.getRepositoryPermission(repository).permission);
	}

	/**
	 * PUSH_PUSH = PUSH access restriction, PUSH access permission
	 */
	@Test
	public void testTeam_PUSH_PUSH() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.PUSH;

		TeamModel team = new TeamModel("test");
		team.setRepositoryPermission(repository.name, AccessPermission.PUSH);

		assertTrue("team CAN NOT view!", team.canView(repository));
		assertTrue("team CAN NOT clone!", team.canClone(repository));
		assertTrue("team CAN NOT push!", team.canPush(repository));

		assertFalse("team CAN create ref!", team.canCreateRef(repository));
		assertFalse("team CAN delete ref!", team.canDeleteRef(repository));
		assertFalse("team CAN rewind ref!", team.canRewindRef(repository));

		assertEquals("team has wrong permission!", AccessPermission.PUSH, team.getRepositoryPermission(repository).permission);
	}

	/**
	 * CLONE_PUSH = CLONE access restriction, PUSH access permission
	 */
	@Test
	public void testTeam_CLONE_PUSH() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.CLONE;

		TeamModel team = new TeamModel("test");
		team.setRepositoryPermission(repository.name, AccessPermission.PUSH);

		assertTrue("team CAN NOT view!", team.canView(repository));
		assertTrue("team CAN NOT clone!", team.canClone(repository));
		assertTrue("team CAN NOT push!", team.canPush(repository));

		assertFalse("team CAN create ref!", team.canCreateRef(repository));
		assertFalse("team CAN delete ref!", team.canDeleteRef(repository));
		assertFalse("team CAN rewind ref!", team.canRewindRef(repository));

		assertEquals("team has wrong permission!", AccessPermission.PUSH, team.getRepositoryPermission(repository).permission);
	}

	/**
	 * VIEW_PUSH = VIEW access restriction, PUSH access permission
	 */
	@Test
	public void testTeam_VIEW_PUSH() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.VIEW;

		TeamModel team = new TeamModel("test");
		team.setRepositoryPermission(repository.name, AccessPermission.PUSH);

		assertTrue("team CAN NOT view!", team.canView(repository));
		assertTrue("team CAN NOT clone!", team.canClone(repository));
		assertTrue("team CAN NOT push!", team.canPush(repository));

		assertFalse("team CAN create ref!", team.canCreateRef(repository));
		assertFalse("team CAN delete ref!", team.canDeleteRef(repository));
		assertFalse("team CAN rewind ref!", team.canRewindRef(repository));

		assertEquals("team has wrong permission!", AccessPermission.PUSH, team.getRepositoryPermission(repository).permission);
	}

	/**
	 * NONE_CREATE = NO access restriction, CREATE access permission
	 * (not useful scenario)
	 */
	@Test
	public void testTeam_NONE_CREATE() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.NONE;

		TeamModel team = new TeamModel("test");
		team.setRepositoryPermission(repository.name, AccessPermission.CREATE);

		assertTrue("team CAN NOT view!", team.canView(repository));
		assertTrue("team CAN NOT clone!", team.canClone(repository));
		assertTrue("team CAN NOT push!", team.canPush(repository));

		assertTrue("team CAN NOT create ref!", team.canCreateRef(repository));
		assertTrue("team CAN NOT delete ref!", team.canDeleteRef(repository));
		assertTrue("team CAN NOT rewind ref!", team.canRewindRef(repository));

		assertEquals("team has wrong permission!", AccessPermission.REWIND, team.getRepositoryPermission(repository).permission);
	}

	/**
	 * PUSH_CREATE = PUSH access restriction, CREATE access permission
	 */
	@Test
	public void testTeam_PUSH_CREATE() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.PUSH;

		TeamModel team = new TeamModel("test");
		team.setRepositoryPermission(repository.name, AccessPermission.CREATE);

		assertTrue("team CAN NOT view!", team.canView(repository));
		assertTrue("team CAN NOT clone!", team.canClone(repository));
		assertTrue("team CAN NOT push!", team.canPush(repository));

		assertTrue("team CAN NOT create ref!", team.canCreateRef(repository));
		assertFalse("team CAN delete ref!", team.canDeleteRef(repository));
		assertFalse("team CAN rewind ref!", team.canRewindRef(repository));

		assertEquals("team has wrong permission!", AccessPermission.CREATE, team.getRepositoryPermission(repository).permission);
	}

	/**
	 * CLONE_CREATE = CLONE access restriction, CREATE access permission
	 */
	@Test
	public void testTeam_CLONE_CREATE() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.CLONE;

		TeamModel team = new TeamModel("test");
		team.setRepositoryPermission(repository.name, AccessPermission.CREATE);

		assertTrue("team CAN NOT view!", team.canView(repository));
		assertTrue("team CAN NOT clone!", team.canClone(repository));
		assertTrue("team CAN NOT push!", team.canPush(repository));

		assertTrue("team CAN NOT create ref!", team.canCreateRef(repository));
		assertFalse("team CAN delete ref!", team.canDeleteRef(repository));
		assertFalse("team CAN rewind ref!", team.canRewindRef(repository));

		assertEquals("team has wrong permission!", AccessPermission.CREATE, team.getRepositoryPermission(repository).permission);
	}

	/**
	 * VIEW_CREATE = VIEW access restriction, CREATE access permission
	 */
	@Test
	public void testTeam_VIEW_CREATE() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.VIEW;

		TeamModel team = new TeamModel("test");
		team.setRepositoryPermission(repository.name, AccessPermission.CREATE);

		assertTrue("team CAN NOT view!", team.canView(repository));
		assertTrue("team CAN NOT clone!", team.canClone(repository));
		assertTrue("team CAN NOT push!", team.canPush(repository));

		assertTrue("team CAN NOT create ref!", team.canCreateRef(repository));
		assertFalse("team CAN delete ref!", team.canDeleteRef(repository));
		assertFalse("team CAN rewind ref!", team.canRewindRef(repository));

		assertEquals("team has wrong permission!", AccessPermission.CREATE, team.getRepositoryPermission(repository).permission);
	}

	/**
	 * NONE_DELETE = NO access restriction, DELETE access permission
	 * (not useful scenario)
	 */
	@Test
	public void testTeam_NONE_DELETE() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.NONE;

		TeamModel team = new TeamModel("test");
		team.setRepositoryPermission(repository.name, AccessPermission.DELETE);

		assertTrue("team CAN NOT view!", team.canView(repository));
		assertTrue("team CAN NOT clone!", team.canClone(repository));
		assertTrue("team CAN NOT push!", team.canPush(repository));

		assertTrue("team CAN NOT create ref!", team.canCreateRef(repository));
		assertTrue("team CAN NOT delete ref!", team.canDeleteRef(repository));
		assertTrue("team CAN NOT rewind ref!", team.canRewindRef(repository));

		assertEquals("team has wrong permission!", AccessPermission.REWIND, team.getRepositoryPermission(repository).permission);
	}

	/**
	 * PUSH_DELETE = PUSH access restriction, DELETE access permission
	 */
	@Test
	public void testTeam_PUSH_DELETE() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.PUSH;

		TeamModel team = new TeamModel("test");
		team.setRepositoryPermission(repository.name, AccessPermission.DELETE);

		assertTrue("team CAN NOT view!", team.canView(repository));
		assertTrue("team CAN NOT clone!", team.canClone(repository));
		assertTrue("team CAN NOT push!", team.canPush(repository));

		assertTrue("team CAN NOT create ref!", team.canCreateRef(repository));
		assertTrue("team CAN NOT delete ref!", team.canDeleteRef(repository));
		assertFalse("team CAN rewind ref!", team.canRewindRef(repository));

		assertEquals("team has wrong permission!", AccessPermission.DELETE, team.getRepositoryPermission(repository).permission);
	}

	/**
	 * CLONE_DELETE = CLONE access restriction, DELETE access permission
	 */
	@Test
	public void testTeam_CLONE_DELETE() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.CLONE;

		TeamModel team = new TeamModel("test");
		team.setRepositoryPermission(repository.name, AccessPermission.DELETE);

		assertTrue("team CAN NOT view!", team.canView(repository));
		assertTrue("team CAN NOT clone!", team.canClone(repository));
		assertTrue("team CAN NOT push!", team.canPush(repository));

		assertTrue("team CAN NOT create ref!", team.canCreateRef(repository));
		assertTrue("team CAN NOT delete ref!", team.canDeleteRef(repository));
		assertFalse("team CAN rewind ref!", team.canRewindRef(repository));

		assertEquals("team has wrong permission!", AccessPermission.DELETE, team.getRepositoryPermission(repository).permission);
	}

	/**
	 * VIEW_DELETE = VIEW access restriction, DELETE access permission
	 */
	@Test
	public void testTeam_VIEW_DELETE() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.VIEW;

		TeamModel team = new TeamModel("test");
		team.setRepositoryPermission(repository.name, AccessPermission.DELETE);

		assertTrue("team CAN NOT view!", team.canView(repository));
		assertTrue("team CAN NOT clone!", team.canClone(repository));
		assertTrue("team CAN NOT push!", team.canPush(repository));

		assertTrue("team CAN NOT create ref!", team.canCreateRef(repository));
		assertTrue("team CAN NOT delete ref!", team.canDeleteRef(repository));
		assertFalse("team CAN rewind ref!", team.canRewindRef(repository));

		assertEquals("team has wrong permission!", AccessPermission.DELETE, team.getRepositoryPermission(repository).permission);
	}

	/**
	 * NONE_REWIND = NO access restriction, REWIND access permission
	 * (not useful scenario)
	 */
	@Test
	public void testTeam_NONE_REWIND() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.NONE;

		TeamModel team = new TeamModel("test");
		team.setRepositoryPermission(repository.name, AccessPermission.REWIND);

		assertTrue("team CAN NOT view!", team.canView(repository));
		assertTrue("team CAN NOT clone!", team.canClone(repository));
		assertTrue("team CAN NOT push!", team.canPush(repository));

		assertTrue("team CAN NOT create ref!", team.canCreateRef(repository));
		assertTrue("team CAN NOT delete ref!", team.canDeleteRef(repository));
		assertTrue("team CAN NOT rewind ref!", team.canRewindRef(repository));

		assertEquals("team has wrong permission!", AccessPermission.REWIND, team.getRepositoryPermission(repository).permission);
	}

	/**
	 * PUSH_REWIND = PUSH access restriction, REWIND access permission
	 */
	@Test
	public void testTeam_PUSH_REWIND() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.PUSH;

		TeamModel team = new TeamModel("test");
		team.setRepositoryPermission(repository.name, AccessPermission.REWIND);

		assertTrue("team CAN NOT view!", team.canView(repository));
		assertTrue("team CAN NOT clone!", team.canClone(repository));
		assertTrue("team CAN NOT push!", team.canPush(repository));

		assertTrue("team CAN NOT create ref!", team.canCreateRef(repository));
		assertTrue("team CAN NOT delete ref!", team.canDeleteRef(repository));
		assertTrue("team CAN NOT rewind ref!", team.canRewindRef(repository));

		assertEquals("team has wrong permission!", AccessPermission.REWIND, team.getRepositoryPermission(repository).permission);
	}

	/**
	 * CLONE_REWIND = CLONE access restriction, REWIND access permission
	 */
	@Test
	public void testTeam_CLONE_REWIND() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.CLONE;

		TeamModel team = new TeamModel("test");
		team.setRepositoryPermission(repository.name, AccessPermission.REWIND);

		assertTrue("team CAN NOT view!", team.canView(repository));
		assertTrue("team CAN NOT clone!", team.canClone(repository));
		assertTrue("team CAN NOT push!", team.canPush(repository));

		assertTrue("team CAN NOT create ref!", team.canCreateRef(repository));
		assertTrue("team CAN NOT delete ref!", team.canDeleteRef(repository));
		assertTrue("team CAN NOT rewind ref!", team.canRewindRef(repository));

		assertEquals("team has wrong permission!", AccessPermission.REWIND, team.getRepositoryPermission(repository).permission);
	}

	/**
	 * VIEW_REWIND = VIEW access restriction, REWIND access permission
	 */
	@Test
	public void testTeam_VIEW_REWIND() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.VIEW;

		TeamModel team = new TeamModel("test");
		team.setRepositoryPermission(repository.name, AccessPermission.REWIND);

		assertTrue("team CAN NOT view!", team.canView(repository));
		assertTrue("team CAN NOT clone!", team.canClone(repository));
		assertTrue("team CAN NOT push!", team.canPush(repository));

		assertTrue("team CAN NOT create ref!", team.canCreateRef(repository));
		assertTrue("team CAN NOT delete ref!", team.canDeleteRef(repository));
		assertTrue("team CAN NOT rewind ref!", team.canRewindRef(repository));

		assertEquals("team has wrong permission!", AccessPermission.REWIND, team.getRepositoryPermission(repository).permission);
	}

	/**
	 * NONE_CLONE = NO access restriction, CLONE access permission
	 * (not useful scenario)
	 */
	@Test
	public void testTeam_NONE_CLONE() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.NONE;

		TeamModel team = new TeamModel("test");
		team.setRepositoryPermission(repository.name, AccessPermission.CLONE);

		assertTrue("team CAN NOT view!", team.canView(repository));
		assertTrue("team CAN NOT clone!", team.canClone(repository));
		assertTrue("team CAN NOT push!", team.canPush(repository));

		assertTrue("team CAN NOT create ref!", team.canCreateRef(repository));
		assertTrue("team CAN NOT delete ref!", team.canDeleteRef(repository));
		assertTrue("team CAN NOT rewind ref!", team.canRewindRef(repository));

		assertEquals("team has wrong permission!", AccessPermission.REWIND, team.getRepositoryPermission(repository).permission);
	}

	/**
	 * PUSH_CLONE = PUSH access restriction, CLONE access permission
	 */
	@Test
	public void testTeam_PUSH_CLONE() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.PUSH;

		TeamModel team = new TeamModel("test");
		team.setRepositoryPermission(repository.name, AccessPermission.CLONE);

		assertTrue("team CAN NOT view!", team.canView(repository));
		assertTrue("team CAN NOT clone!", team.canClone(repository));
		assertFalse("team CAN push!", team.canPush(repository));

		assertFalse("team CAN create ref!", team.canCreateRef(repository));
		assertFalse("team CAN delete ref!", team.canDeleteRef(repository));
		assertFalse("team CAN rewind ref!", team.canRewindRef(repository));

		assertEquals("team has wrong permission!", AccessPermission.CLONE, team.getRepositoryPermission(repository).permission);
	}

	/**
	 * CLONE_CLONE = CLONE access restriction, CLONE access permission
	 */
	@Test
	public void testTeam_CLONE_CLONE() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.CLONE;

		TeamModel team = new TeamModel("test");
		team.setRepositoryPermission(repository.name, AccessPermission.CLONE);

		assertTrue("team CAN NOT view!", team.canView(repository));
		assertTrue("team CAN NOT clone!", team.canClone(repository));
		assertFalse("team CAN push!", team.canPush(repository));

		assertFalse("team CAN create ref!", team.canCreateRef(repository));
		assertFalse("team CAN delete ref!", team.canDeleteRef(repository));
		assertFalse("team CAN rewind ref!", team.canRewindRef(repository));

		assertEquals("team has wrong permission!", AccessPermission.CLONE, team.getRepositoryPermission(repository).permission);
	}

	/**
	 * VIEW_CLONE = VIEW access restriction, CLONE access permission
	 */
	@Test
	public void testTeam_VIEW_CLONE() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.VIEW;

		TeamModel team = new TeamModel("test");
		team.setRepositoryPermission(repository.name, AccessPermission.CLONE);

		assertTrue("team CAN NOT view!", team.canView(repository));
		assertTrue("team CAN NOT clone!", team.canClone(repository));
		assertFalse("team CAN push!", team.canPush(repository));

		assertFalse("team CAN create ref!", team.canCreateRef(repository));
		assertFalse("team CAN delete ref!", team.canDeleteRef(repository));
		assertFalse("team CAN rewind ref!", team.canRewindRef(repository));

		assertEquals("team has wrong permission!", AccessPermission.CLONE, team.getRepositoryPermission(repository).permission);
	}

	/**
	 * NONE_VIEW = NO access restriction, VIEW access permission
	 * (not useful scenario)
	 */
	@Test
	public void testTeam_NONE_VIEW() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.NONE;

		TeamModel team = new TeamModel("test");
		team.setRepositoryPermission(repository.name, AccessPermission.VIEW);

		assertTrue("team CAN NOT view!", team.canView(repository));
		assertTrue("team CAN NOT clone!", team.canClone(repository));
		assertTrue("team CAN NOT push!", team.canPush(repository));

		assertTrue("team CAN NOT create ref!", team.canCreateRef(repository));
		assertTrue("team CAN NOT delete ref!", team.canDeleteRef(repository));
		assertTrue("team CAN NOT rewind ref!", team.canRewindRef(repository));

		assertEquals("team has wrong permission!", AccessPermission.REWIND, team.getRepositoryPermission(repository).permission);
	}

	/**
	 * PUSH_VIEW = PUSH access restriction, VIEW access permission
	 */
	@Test
	public void testTeam_PUSH_VIEW() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.PUSH;

		TeamModel team = new TeamModel("test");
		team.setRepositoryPermission(repository.name, AccessPermission.VIEW);

		assertTrue("team CAN NOT view!", team.canView(repository));
		assertTrue("team CAN NOT clone!", team.canClone(repository));
		assertFalse("team CAN push!", team.canPush(repository));

		assertFalse("team CAN create ref!", team.canCreateRef(repository));
		assertFalse("team CAN delete ref!", team.canDeleteRef(repository));
		assertFalse("team CAN rewind ref!", team.canRewindRef(repository));

		assertEquals("team has wrong permission!", AccessPermission.CLONE, team.getRepositoryPermission(repository).permission);
	}

	/**
	 * CLONE_VIEW = CLONE access restriction, VIEW access permission
	 */
	@Test
	public void testTeam_CLONE_VIEW() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.CLONE;

		TeamModel team = new TeamModel("test");
		team.setRepositoryPermission(repository.name, AccessPermission.VIEW);

		assertTrue("team CAN NOT view!", team.canView(repository));
		assertFalse("team CAN clone!", team.canClone(repository));
		assertFalse("team CAN push!", team.canPush(repository));

		assertFalse("team CAN create ref!", team.canCreateRef(repository));
		assertFalse("team CAN delete ref!", team.canDeleteRef(repository));
		assertFalse("team CAN rewind ref!", team.canRewindRef(repository));

		assertEquals("team has wrong permission!", AccessPermission.VIEW, team.getRepositoryPermission(repository).permission);
	}

	/**
	 * VIEW_VIEW = VIEW access restriction, VIEW access permission
	 */
	@Test
	public void testTeam_VIEW_VIEW() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.VIEW;

		TeamModel team = new TeamModel("test");
		team.setRepositoryPermission(repository.name, AccessPermission.VIEW);

		assertTrue("team CAN NOT view!", team.canView(repository));
		assertFalse("team CAN clone!", team.canClone(repository));
		assertFalse("team CAN push!", team.canPush(repository));

		assertFalse("team CAN create ref!", team.canCreateRef(repository));
		assertFalse("team CAN delete ref!", team.canDeleteRef(repository));
		assertFalse("team CAN rewind ref!", team.canRewindRef(repository));

		assertEquals("team has wrong permission!", AccessPermission.VIEW, team.getRepositoryPermission(repository).permission);
	}

	/**
	 * NONE_NONE = NO access restriction, NO access permission
	 */
	@Test
	public void testTeamMember_NONE_NONE() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.NONE;

		TeamModel team = new TeamModel("test");
		UserModel user = new UserModel("test");
		user.addTeam(team);

		assertTrue("team member CAN NOT view!", user.canView(repository));
		assertTrue("team member CAN NOT clone!", user.canClone(repository));
		assertTrue("team member CAN NOT push!", user.canPush(repository));

		assertTrue("team member CAN NOT create ref!", user.canCreateRef(repository));
		assertTrue("team member CAN NOT delete ref!", user.canDeleteRef(repository));
		assertTrue("team member CAN NOT rewind ref!", user.canRewindRef(repository));

		assertEquals("team member has wrong permission!", AccessPermission.REWIND, user.getRepositoryPermission(repository).permission);
	}

	/**
	 * PUSH_NONE = PUSH access restriction, NO access permission
	 */
	@Test
	public void testTeamMember_PUSH_NONE() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.PUSH;

		TeamModel team = new TeamModel("test");
		UserModel user = new UserModel("test");
		user.addTeam(team);

		assertTrue("team member CAN NOT view!", user.canView(repository));
		assertTrue("team member CAN NOT clone!", user.canClone(repository));
		assertFalse("team member CAN push!", user.canPush(repository));

		assertFalse("team member CAN create ref!", user.canCreateRef(repository));
		assertFalse("team member CAN delete ref!", user.canDeleteRef(repository));
		assertFalse("team member CAN rewind ref!", user.canRewindRef(repository));

		assertEquals("team member has wrong permission!", AccessPermission.CLONE, user.getRepositoryPermission(repository).permission);
	}

	/**
	 * CLONE_NONE = CLONE access restriction, NO access permission
	 */
	@Test
	public void testTeamMember_CLONE_NONE() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.CLONE;

		TeamModel team = new TeamModel("test");
		UserModel user = new UserModel("test");
		user.addTeam(team);

		assertTrue("team member CAN NOT view!", user.canView(repository));
		assertFalse("team member CAN clone!", user.canClone(repository));
		assertFalse("team member CAN push!", user.canPush(repository));

		assertFalse("team member CAN create ref!", user.canCreateRef(repository));
		assertFalse("team member CAN delete ref!", user.canDeleteRef(repository));
		assertFalse("team member CAN rewind ref!", user.canRewindRef(repository));

		assertEquals("team member has wrong permission!", AccessPermission.VIEW, user.getRepositoryPermission(repository).permission);
	}

	/**
	 * VIEW_NONE = VIEW access restriction, NO access permission
	 */
	@Test
	public void testTeamMember_VIEW_NONE() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.VIEW;

		TeamModel team = new TeamModel("test");
		UserModel user = new UserModel("test");
		user.addTeam(team);

		assertFalse("team member CAN view!", user.canView(repository));
		assertFalse("team member CAN clone!", user.canClone(repository));
		assertFalse("team member CAN push!", user.canPush(repository));

		assertFalse("team member CAN create ref!", user.canCreateRef(repository));
		assertFalse("team member CAN delete ref!", user.canDeleteRef(repository));
		assertFalse("team member CAN rewind ref!", user.canRewindRef(repository));

		assertEquals("team member has wrong permission!", AccessPermission.NONE, user.getRepositoryPermission(repository).permission);
	}

	/**
	 * NONE_PUSH = NO access restriction, PUSH access permission
	 * (not useful scenario)
	 */
	@Test
	public void testTeamMember_NONE_PUSH() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.NONE;

		TeamModel team = new TeamModel("test");
		team.setRepositoryPermission(repository.name, AccessPermission.PUSH);
		UserModel user = new UserModel("test");
		user.addTeam(team);

		assertTrue("team member CAN NOT view!", user.canView(repository));
		assertTrue("team member CAN NOT clone!", user.canClone(repository));
		assertTrue("team member CAN NOT push!", user.canPush(repository));

		assertTrue("team member CAN NOT create ref!", user.canCreateRef(repository));
		assertTrue("team member CAN NOT delete ref!", user.canDeleteRef(repository));
		assertTrue("team member CAN NOT rewind ref!", user.canRewindRef(repository));

		assertEquals("team member has wrong permission!", AccessPermission.REWIND, user.getRepositoryPermission(repository).permission);
	}

	/**
	 * PUSH_PUSH = PUSH access restriction, PUSH access permission
	 */
	@Test
	public void testTeamMember_PUSH_PUSH() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.PUSH;

		TeamModel team = new TeamModel("test");
		team.setRepositoryPermission(repository.name, AccessPermission.PUSH);
		UserModel user = new UserModel("test");
		user.addTeam(team);

		assertTrue("team member CAN NOT view!", user.canView(repository));
		assertTrue("team member CAN NOT clone!", user.canClone(repository));
		assertTrue("team member CAN NOT push!", user.canPush(repository));

		assertFalse("team member CAN create ref!", user.canCreateRef(repository));
		assertFalse("team member CAN delete ref!", user.canDeleteRef(repository));
		assertFalse("team member CAN rewind ref!", user.canRewindRef(repository));

		assertEquals("team member has wrong permission!", AccessPermission.PUSH, user.getRepositoryPermission(repository).permission);
	}

	/**
	 * CLONE_PUSH = CLONE access restriction, PUSH access permission
	 */
	@Test
	public void testTeamMember_CLONE_PUSH() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.CLONE;

		TeamModel team = new TeamModel("test");
		team.setRepositoryPermission(repository.name, AccessPermission.PUSH);
		UserModel user = new UserModel("test");
		user.addTeam(team);

		assertTrue("team member CAN NOT view!", user.canView(repository));
		assertTrue("team member CAN NOT clone!", user.canClone(repository));
		assertTrue("team member CAN NOT push!", user.canPush(repository));

		assertFalse("team member CAN create ref!", user.canCreateRef(repository));
		assertFalse("team member CAN delete ref!", user.canDeleteRef(repository));
		assertFalse("team member CAN rewind ref!", user.canRewindRef(repository));

		assertEquals("team member has wrong permission!", AccessPermission.PUSH, user.getRepositoryPermission(repository).permission);
	}

	/**
	 * VIEW_PUSH = VIEW access restriction, PUSH access permission
	 */
	@Test
	public void testTeamMember_VIEW_PUSH() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.VIEW;

		TeamModel team = new TeamModel("test");
		team.setRepositoryPermission(repository.name, AccessPermission.PUSH);
		UserModel user = new UserModel("test");
		user.addTeam(team);

		assertTrue("team member CAN NOT view!", user.canView(repository));
		assertTrue("team member CAN NOT clone!", user.canClone(repository));
		assertTrue("team member CAN NOT push!", user.canPush(repository));

		assertFalse("team member CAN create ref!", user.canCreateRef(repository));
		assertFalse("team member CAN delete ref!", user.canDeleteRef(repository));
		assertFalse("team member CAN rewind ref!", user.canRewindRef(repository));

		assertEquals("team member has wrong permission!", AccessPermission.PUSH, user.getRepositoryPermission(repository).permission);
	}

	/**
	 * NONE_CREATE = NO access restriction, CREATE access permission
	 * (not useful scenario)
	 */
	@Test
	public void testTeamMember_NONE_CREATE() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.NONE;

		TeamModel team = new TeamModel("test");
		team.setRepositoryPermission(repository.name, AccessPermission.CREATE);
		UserModel user = new UserModel("test");
		user.addTeam(team);

		assertTrue("team member CAN NOT view!", user.canView(repository));
		assertTrue("team member CAN NOT clone!", user.canClone(repository));
		assertTrue("team member CAN NOT push!", user.canPush(repository));

		assertTrue("team member CAN NOT create ref!", user.canCreateRef(repository));
		assertTrue("team member CAN NOT delete ref!", user.canDeleteRef(repository));
		assertTrue("team member CAN NOT rewind ref!", user.canRewindRef(repository));

		assertEquals("team member has wrong permission!", AccessPermission.REWIND, user.getRepositoryPermission(repository).permission);
	}

	/**
	 * PUSH_CREATE = PUSH access restriction, CREATE access permission
	 */
	@Test
	public void testTeamMember_PUSH_CREATE() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.PUSH;

		TeamModel team = new TeamModel("test");
		team.setRepositoryPermission(repository.name, AccessPermission.CREATE);
		UserModel user = new UserModel("test");
		user.addTeam(team);

		assertTrue("team member CAN NOT view!", user.canView(repository));
		assertTrue("team member CAN NOT clone!", user.canClone(repository));
		assertTrue("team member CAN NOT push!", user.canPush(repository));

		assertTrue("team member CAN NOT create ref!", user.canCreateRef(repository));
		assertFalse("team member CAN delete ref!", user.canDeleteRef(repository));
		assertFalse("team member CAN rewind ref!", user.canRewindRef(repository));

		assertEquals("team member has wrong permission!", AccessPermission.CREATE, user.getRepositoryPermission(repository).permission);
	}

	/**
	 * CLONE_CREATE = CLONE access restriction, CREATE access permission
	 */
	@Test
	public void testTeamMember_CLONE_CREATE() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.CLONE;

		TeamModel team = new TeamModel("test");
		team.setRepositoryPermission(repository.name, AccessPermission.CREATE);
		UserModel user = new UserModel("test");
		user.addTeam(team);

		assertTrue("team member CAN NOT view!", user.canView(repository));
		assertTrue("team member CAN NOT clone!", user.canClone(repository));
		assertTrue("team member CAN NOT push!", user.canPush(repository));

		assertTrue("team member CAN NOT create ref!", user.canCreateRef(repository));
		assertFalse("team member CAN delete ref!", user.canDeleteRef(repository));
		assertFalse("team member CAN rewind ref!", user.canRewindRef(repository));

		assertEquals("team member has wrong permission!", AccessPermission.CREATE, user.getRepositoryPermission(repository).permission);
	}

	/**
	 * VIEW_CREATE = VIEW access restriction, CREATE access permission
	 */
	@Test
	public void testTeamMember_VIEW_CREATE() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.VIEW;

		TeamModel team = new TeamModel("test");
		team.setRepositoryPermission(repository.name, AccessPermission.CREATE);
		UserModel user = new UserModel("test");
		user.addTeam(team);

		assertTrue("team member CAN NOT view!", user.canView(repository));
		assertTrue("team member CAN NOT clone!", user.canClone(repository));
		assertTrue("team member CAN NOT push!", user.canPush(repository));

		assertTrue("team member CAN NOT create ref!", user.canCreateRef(repository));
		assertFalse("team member CAN delete ref!", user.canDeleteRef(repository));
		assertFalse("team member CAN rewind ref!", user.canRewindRef(repository));

		assertEquals("team member has wrong permission!", AccessPermission.CREATE, user.getRepositoryPermission(repository).permission);
	}

	/**
	 * NONE_DELETE = NO access restriction, DELETE access permission
	 * (not useful scenario)
	 */
	@Test
	public void testTeamMember_NONE_DELETE() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.NONE;

		TeamModel team = new TeamModel("test");
		team.setRepositoryPermission(repository.name, AccessPermission.DELETE);
		UserModel user = new UserModel("test");
		user.addTeam(team);

		assertTrue("team member CAN NOT view!", user.canView(repository));
		assertTrue("team member CAN NOT clone!", user.canClone(repository));
		assertTrue("team member CAN NOT push!", user.canPush(repository));

		assertTrue("team member CAN NOT create ref!", user.canCreateRef(repository));
		assertTrue("team member CAN NOT delete ref!", user.canDeleteRef(repository));
		assertTrue("team member CAN NOT rewind ref!", user.canRewindRef(repository));

		assertEquals("team member has wrong permission!", AccessPermission.REWIND, user.getRepositoryPermission(repository).permission);
	}

	/**
	 * PUSH_DELETE = PUSH access restriction, DELETE access permission
	 */
	@Test
	public void testTeamMember_PUSH_DELETE() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.PUSH;

		TeamModel team = new TeamModel("test");
		team.setRepositoryPermission(repository.name, AccessPermission.DELETE);
		UserModel user = new UserModel("test");
		user.addTeam(team);

		assertTrue("team member CAN NOT view!", user.canView(repository));
		assertTrue("team member CAN NOT clone!", user.canClone(repository));
		assertTrue("team member CAN NOT push!", user.canPush(repository));

		assertTrue("team member CAN NOT create ref!", user.canCreateRef(repository));
		assertTrue("team member CAN NOT delete ref!", user.canDeleteRef(repository));
		assertFalse("team member CAN rewind ref!", user.canRewindRef(repository));

		assertEquals("team member has wrong permission!", AccessPermission.DELETE, user.getRepositoryPermission(repository).permission);
	}

	/**
	 * CLONE_DELETE = CLONE access restriction, DELETE access permission
	 */
	@Test
	public void testTeamMember_CLONE_DELETE() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.CLONE;

		TeamModel team = new TeamModel("test");
		team.setRepositoryPermission(repository.name, AccessPermission.DELETE);
		UserModel user = new UserModel("test");
		user.addTeam(team);

		assertTrue("team member CAN NOT view!", user.canView(repository));
		assertTrue("team member CAN NOT clone!", user.canClone(repository));
		assertTrue("team member CAN NOT push!", user.canPush(repository));

		assertTrue("team member CAN NOT create ref!", user.canCreateRef(repository));
		assertTrue("team member CAN NOT delete ref!", user.canDeleteRef(repository));
		assertFalse("team member CAN rewind ref!", user.canRewindRef(repository));

		assertEquals("team member has wrong permission!", AccessPermission.DELETE, user.getRepositoryPermission(repository).permission);
	}

	/**
	 * VIEW_DELETE = VIEW access restriction, DELETE access permission
	 */
	@Test
	public void testTeamMember_VIEW_DELETE() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.VIEW;

		TeamModel team = new TeamModel("test");
		team.setRepositoryPermission(repository.name, AccessPermission.DELETE);
		UserModel user = new UserModel("test");
		user.addTeam(team);

		assertTrue("team member CAN NOT view!", user.canView(repository));
		assertTrue("team member CAN NOT clone!", user.canClone(repository));
		assertTrue("team member CAN NOT push!", user.canPush(repository));

		assertTrue("team member CAN NOT create ref!", user.canCreateRef(repository));
		assertTrue("team member CAN NOT delete ref!", user.canDeleteRef(repository));
		assertFalse("team member CAN rewind ref!", user.canRewindRef(repository));

		assertEquals("team member has wrong permission!", AccessPermission.DELETE, user.getRepositoryPermission(repository).permission);
	}

	/**
	 * NONE_REWIND = NO access restriction, REWIND access permission
	 * (not useful scenario)
	 */
	@Test
	public void testTeamMember_NONE_REWIND() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.NONE;

		TeamModel team = new TeamModel("test");
		team.setRepositoryPermission(repository.name, AccessPermission.REWIND);
		UserModel user = new UserModel("test");
		user.addTeam(team);

		assertTrue("team member CAN NOT view!", user.canView(repository));
		assertTrue("team member CAN NOT clone!", user.canClone(repository));
		assertTrue("team member CAN NOT push!", user.canPush(repository));

		assertTrue("team member CAN NOT create ref!", user.canCreateRef(repository));
		assertTrue("team member CAN NOT delete ref!", user.canDeleteRef(repository));
		assertTrue("team member CAN NOT rewind ref!", user.canRewindRef(repository));

		assertEquals("team member has wrong permission!", AccessPermission.REWIND, user.getRepositoryPermission(repository).permission);
	}

	/**
	 * PUSH_REWIND = PUSH access restriction, REWIND access permission
	 */
	@Test
	public void testTeamMember_PUSH_REWIND() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.PUSH;

		TeamModel team = new TeamModel("test");
		team.setRepositoryPermission(repository.name, AccessPermission.REWIND);
		UserModel user = new UserModel("test");
		user.addTeam(team);

		assertTrue("team member CAN NOT view!", user.canView(repository));
		assertTrue("team member CAN NOT clone!", user.canClone(repository));
		assertTrue("team member CAN NOT push!", user.canPush(repository));

		assertTrue("team member CAN NOT create ref!", user.canCreateRef(repository));
		assertTrue("team member CAN NOT delete ref!", user.canDeleteRef(repository));
		assertTrue("team member CAN NOT rewind ref!", user.canRewindRef(repository));

		assertEquals("team member has wrong permission!", AccessPermission.REWIND, user.getRepositoryPermission(repository).permission);
	}

	/**
	 * CLONE_REWIND = CLONE access restriction, REWIND access permission
	 */
	@Test
	public void testTeamMember_CLONE_REWIND() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.CLONE;

		TeamModel team = new TeamModel("test");
		team.setRepositoryPermission(repository.name, AccessPermission.REWIND);
		UserModel user = new UserModel("test");
		user.addTeam(team);

		assertTrue("team member CAN NOT view!", user.canView(repository));
		assertTrue("team member CAN NOT clone!", user.canClone(repository));
		assertTrue("team member CAN NOT push!", user.canPush(repository));

		assertTrue("team member CAN NOT create ref!", user.canCreateRef(repository));
		assertTrue("team member CAN NOT delete ref!", user.canDeleteRef(repository));
		assertTrue("team member CAN NOT rewind ref!", user.canRewindRef(repository));

		assertEquals("team member has wrong permission!", AccessPermission.REWIND, user.getRepositoryPermission(repository).permission);
	}

	/**
	 * VIEW_REWIND = VIEW access restriction, REWIND access permission
	 */
	@Test
	public void testTeamMember_VIEW_REWIND() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.VIEW;

		TeamModel team = new TeamModel("test");
		team.setRepositoryPermission(repository.name, AccessPermission.REWIND);
		UserModel user = new UserModel("test");
		user.addTeam(team);

		assertTrue("team member CAN NOT view!", user.canView(repository));
		assertTrue("team member CAN NOT clone!", user.canClone(repository));
		assertTrue("team member CAN NOT push!", user.canPush(repository));

		assertTrue("team member CAN NOT create ref!", user.canCreateRef(repository));
		assertTrue("team member CAN NOT delete ref!", user.canDeleteRef(repository));
		assertTrue("team member CAN NOT rewind ref!", user.canRewindRef(repository));

		assertEquals("team member has wrong permission!", AccessPermission.REWIND, user.getRepositoryPermission(repository).permission);
	}

	/**
	 * NONE_CLONE = NO access restriction, CLONE access permission
	 * (not useful scenario)
	 */
	@Test
	public void testTeamMember_NONE_CLONE() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.NONE;

		TeamModel team = new TeamModel("test");
		team.setRepositoryPermission(repository.name, AccessPermission.CLONE);
		UserModel user = new UserModel("test");
		user.addTeam(team);

		assertTrue("team member CAN NOT view!", user.canView(repository));
		assertTrue("team member CAN NOT clone!", user.canClone(repository));
		assertTrue("team member CAN NOT push!", user.canPush(repository));

		assertTrue("team member CAN NOT create ref!", user.canCreateRef(repository));
		assertTrue("team member CAN NOT delete ref!", user.canDeleteRef(repository));
		assertTrue("team member CAN NOT rewind ref!", user.canRewindRef(repository));

		assertEquals("team member has wrong permission!", AccessPermission.REWIND, user.getRepositoryPermission(repository).permission);
	}

	/**
	 * PUSH_CLONE = PUSH access restriction, CLONE access permission
	 */
	@Test
	public void testTeamMember_PUSH_CLONE() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.PUSH;

		TeamModel team = new TeamModel("test");
		team.setRepositoryPermission(repository.name, AccessPermission.CLONE);
		UserModel user = new UserModel("test");
		user.addTeam(team);

		assertTrue("team member CAN NOT view!", user.canView(repository));
		assertTrue("team member CAN NOT clone!", user.canClone(repository));
		assertFalse("team member CAN push!", user.canPush(repository));

		assertFalse("team member CAN create ref!", user.canCreateRef(repository));
		assertFalse("team member CAN delete ref!", user.canDeleteRef(repository));
		assertFalse("team member CAN rewind ref!", user.canRewindRef(repository));

		assertEquals("team member has wrong permission!", AccessPermission.CLONE, user.getRepositoryPermission(repository).permission);
	}

	/**
	 * CLONE_CLONE = CLONE access restriction, CLONE access permission
	 */
	@Test
	public void testTeamMember_CLONE_CLONE() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.CLONE;

		TeamModel team = new TeamModel("test");
		team.setRepositoryPermission(repository.name, AccessPermission.CLONE);
		UserModel user = new UserModel("test");
		user.addTeam(team);

		assertTrue("team member CAN NOT view!", user.canView(repository));
		assertTrue("team member CAN NOT clone!", user.canClone(repository));
		assertFalse("team member CAN push!", user.canPush(repository));

		assertFalse("team member CAN create ref!", user.canCreateRef(repository));
		assertFalse("team member CAN delete ref!", user.canDeleteRef(repository));
		assertFalse("team member CAN rewind ref!", user.canRewindRef(repository));

		assertEquals("team member has wrong permission!", AccessPermission.CLONE, user.getRepositoryPermission(repository).permission);
	}

	/**
	 * VIEW_CLONE = VIEW access restriction, CLONE access permission
	 */
	@Test
	public void testTeamMember_VIEW_CLONE() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.VIEW;

		TeamModel team = new TeamModel("test");
		team.setRepositoryPermission(repository.name, AccessPermission.CLONE);
		UserModel user = new UserModel("test");
		user.addTeam(team);

		assertTrue("team member CAN NOT view!", user.canView(repository));
		assertTrue("team member CAN NOT clone!", user.canClone(repository));
		assertFalse("team member CAN push!", user.canPush(repository));

		assertFalse("team member CAN create ref!", user.canCreateRef(repository));
		assertFalse("team member CAN delete ref!", user.canDeleteRef(repository));
		assertFalse("team member CAN rewind ref!", user.canRewindRef(repository));

		assertEquals("team member has wrong permission!", AccessPermission.CLONE, user.getRepositoryPermission(repository).permission);
	}

	/**
	 * NONE_VIEW = NO access restriction, VIEW access permission
	 * (not useful scenario)
	 */
	@Test
	public void testTeamMember_NONE_VIEW() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.NONE;

		TeamModel team = new TeamModel("test");
		team.setRepositoryPermission(repository.name, AccessPermission.VIEW);
		UserModel user = new UserModel("test");
		user.addTeam(team);

		assertTrue("team member CAN NOT view!", user.canView(repository));
		assertTrue("team member CAN NOT clone!", user.canClone(repository));
		assertTrue("team member CAN NOT push!", user.canPush(repository));

		assertTrue("team member CAN NOT create ref!", user.canCreateRef(repository));
		assertTrue("team member CAN NOT delete ref!", user.canDeleteRef(repository));
		assertTrue("team member CAN NOT rewind ref!", user.canRewindRef(repository));

		assertEquals("team member has wrong permission!", AccessPermission.REWIND, user.getRepositoryPermission(repository).permission);
	}

	/**
	 * PUSH_VIEW = PUSH access restriction, VIEW access permission
	 */
	@Test
	public void testTeamMember_PUSH_VIEW() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.PUSH;

		TeamModel team = new TeamModel("test");
		team.setRepositoryPermission(repository.name, AccessPermission.VIEW);
		UserModel user = new UserModel("test");
		user.addTeam(team);

		assertTrue("team member CAN NOT view!", user.canView(repository));
		assertTrue("team member CAN NOT clone!", user.canClone(repository));
		assertFalse("team member CAN push!", user.canPush(repository));

		assertFalse("team member CAN create ref!", user.canCreateRef(repository));
		assertFalse("team member CAN delete ref!", user.canDeleteRef(repository));
		assertFalse("team member CAN rewind ref!", user.canRewindRef(repository));

		assertEquals("team member has wrong permission!", AccessPermission.CLONE, user.getRepositoryPermission(repository).permission);
	}

	/**
	 * CLONE_VIEW = CLONE access restriction, VIEW access permission
	 */
	@Test
	public void testTeamMember_CLONE_VIEW() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.CLONE;

		TeamModel team = new TeamModel("test");
		team.setRepositoryPermission(repository.name, AccessPermission.VIEW);
		UserModel user = new UserModel("test");
		user.addTeam(team);

		assertTrue("team member CAN NOT view!", user.canView(repository));
		assertFalse("team member CAN clone!", user.canClone(repository));
		assertFalse("team member CAN push!", user.canPush(repository));

		assertFalse("team member CAN create ref!", user.canCreateRef(repository));
		assertFalse("team member CAN delete ref!", user.canDeleteRef(repository));
		assertFalse("team member CAN rewind ref!", user.canRewindRef(repository));

		assertEquals("team member has wrong permission!", AccessPermission.VIEW, user.getRepositoryPermission(repository).permission);
	}

	/**
	 * VIEW_VIEW = VIEW access restriction, VIEW access permission
	 */
	@Test
	public void testTeamMember_VIEW_VIEW() throws Exception {
		RepositoryModel repository = new RepositoryModel("myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.VIEW;

		TeamModel team = new TeamModel("test");
		team.setRepositoryPermission(repository.name, AccessPermission.VIEW);
		UserModel user = new UserModel("test");
		user.addTeam(team);

		assertTrue("team member CAN NOT view!", user.canView(repository));
		assertFalse("team member CAN clone!", user.canClone(repository));
		assertFalse("team member CAN push!", user.canPush(repository));

		assertFalse("team member CAN create ref!", user.canCreateRef(repository));
		assertFalse("team member CAN delete ref!", user.canDeleteRef(repository));
		assertFalse("team member CAN rewind ref!", user.canRewindRef(repository));

		assertEquals("team member has wrong permission!", AccessPermission.VIEW, user.getRepositoryPermission(repository).permission);
	}

	@Test
	public void testOwner() throws Exception {
		RepositoryModel repository = new RepositoryModel("~jj/myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.VIEW;

		UserModel user = new UserModel("test");
		repository.addOwner(user.username);

		assertFalse("user SHOULD NOT HAVE a repository permission!", user.hasRepositoryPermission(repository.name));
		assertTrue("owner CAN NOT view!", user.canView(repository));
		assertTrue("owner CAN NOT clone!", user.canClone(repository));
		assertTrue("owner CAN NOT push!", user.canPush(repository));

		assertTrue("owner CAN NOT create ref!", user.canCreateRef(repository));
		assertTrue("owner CAN NOT delete ref!", user.canDeleteRef(repository));
		assertTrue("owner CAN NOT rewind ref!", user.canRewindRef(repository));

		assertEquals("owner has wrong permission!", AccessPermission.REWIND, user.getRepositoryPermission(repository).permission);

		assertTrue("owner CAN NOT fork!", user.canFork(repository));

		assertFalse("owner CAN NOT delete!", user.canDelete(repository));
		assertTrue("owner CAN NOT edit!", user.canEdit(repository));

		// test personal repo owner
		UserModel jj = new UserModel("jj");
		assertFalse("jj SHOULD NOT HAVE a repository permission!", jj.hasRepositoryPermission(repository.name));
		assertTrue("jj CAN NOT view!", jj.canView(repository));
		assertTrue("jj CAN NOT clone!", jj.canClone(repository));
		assertTrue("jj CAN NOT push!", jj.canPush(repository));

		assertTrue("jj CAN NOT create ref!", jj.canCreateRef(repository));
		assertTrue("jj CAN NOT delete ref!", jj.canDeleteRef(repository));
		assertTrue("jj CAN NOT rewind ref!", jj.canRewindRef(repository));

		assertEquals("jj has wrong permission!", AccessPermission.REWIND, jj.getRepositoryPermission(repository).permission);

		assertFalse("jj CAN fork!", jj.canFork(repository));

		assertTrue("jj CAN NOT delete!", jj.canDelete(repository));
		assertTrue("jj CAN NOT edit!", jj.canEdit(repository));
		assertTrue(repository.isOwner(jj.username));
	}

	@Test
	public void testMultipleOwners() throws Exception {
		RepositoryModel repository = new RepositoryModel("~jj/myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.VIEW;

		UserModel user = new UserModel("test");
		repository.addOwner(user.username);
		UserModel user2 = new UserModel("test2");
		repository.addOwner(user2.username);

		// first owner
		assertFalse("user SHOULD NOT HAVE a repository permission!", user.hasRepositoryPermission(repository.name));
		assertTrue("owner CAN NOT view!", user.canView(repository));
		assertTrue("owner CAN NOT clone!", user.canClone(repository));
		assertTrue("owner CAN NOT push!", user.canPush(repository));

		assertTrue("owner CAN NOT create ref!", user.canCreateRef(repository));
		assertTrue("owner CAN NOT delete ref!", user.canDeleteRef(repository));
		assertTrue("owner CAN NOT rewind ref!", user.canRewindRef(repository));

		assertEquals("owner has wrong permission!", AccessPermission.REWIND, user.getRepositoryPermission(repository).permission);

		assertTrue("owner CAN NOT fork!", user.canFork(repository));

		assertFalse("owner CAN NOT delete!", user.canDelete(repository));
		assertTrue("owner CAN NOT edit!", user.canEdit(repository));

		// second owner
		assertFalse("user SHOULD NOT HAVE a repository permission!", user2.hasRepositoryPermission(repository.name));
		assertTrue("owner CAN NOT view!", user2.canView(repository));
		assertTrue("owner CAN NOT clone!", user2.canClone(repository));
		assertTrue("owner CAN NOT push!", user2.canPush(repository));

		assertTrue("owner CAN NOT create ref!", user2.canCreateRef(repository));
		assertTrue("owner CAN NOT delete ref!", user2.canDeleteRef(repository));
		assertTrue("owner CAN NOT rewind ref!", user2.canRewindRef(repository));

		assertEquals("owner has wrong permission!", AccessPermission.REWIND, user2.getRepositoryPermission(repository).permission);

		assertTrue("owner CAN NOT fork!", user2.canFork(repository));

		assertFalse("owner CAN NOT delete!", user2.canDelete(repository));
		assertTrue("owner CAN NOT edit!", user2.canEdit(repository));

		assertTrue(repository.isOwner(user.username));
		assertTrue(repository.isOwner(user2.username));

		// test personal repo owner
		UserModel jj = new UserModel("jj");
		assertFalse("jj SHOULD NOT HAVE a repository permission!", jj.hasRepositoryPermission(repository.name));
		assertTrue("jj CAN NOT view!", jj.canView(repository));
		assertTrue("jj CAN NOT clone!", jj.canClone(repository));
		assertTrue("jj CAN NOT push!", jj.canPush(repository));

		assertTrue("jj CAN NOT create ref!", jj.canCreateRef(repository));
		assertTrue("jj CAN NOT delete ref!", jj.canDeleteRef(repository));
		assertTrue("jj CAN NOT rewind ref!", jj.canRewindRef(repository));

		assertEquals("jj has wrong permission!", AccessPermission.REWIND, jj.getRepositoryPermission(repository).permission);

		assertFalse("jj CAN fork!", jj.canFork(repository));

		assertTrue("jj CAN NOT delete!", jj.canDelete(repository));
		assertTrue("jj CAN NOT edit!", jj.canEdit(repository));
		assertTrue(repository.isOwner(jj.username));
	}

	@Test
	public void testOwnerPersonalRepository() throws Exception {
		RepositoryModel repository = new RepositoryModel("~test/myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.VIEW;

		UserModel user = new UserModel("test");
		repository.addOwner(user.username);

		assertFalse("user SHOULD NOT HAVE a repository permission!", user.hasRepositoryPermission(repository.name));
		assertTrue("user CAN NOT view!", user.canView(repository));
		assertTrue("user CAN NOT clone!", user.canClone(repository));
		assertTrue("user CAN NOT push!", user.canPush(repository));

		assertTrue("user CAN NOT create ref!", user.canCreateRef(repository));
		assertTrue("user CAN NOT delete ref!", user.canDeleteRef(repository));
		assertTrue("user CAN NOT rewind ref!", user.canRewindRef(repository));

		assertEquals("user has wrong permission!", AccessPermission.REWIND, user.getRepositoryPermission(repository).permission);

		assertFalse("user CAN fork!", user.canFork(repository));

		assertTrue("user CAN NOT delete!", user.canDelete(repository));
		assertTrue("user CAN NOT edit!", user.canEdit(repository));
	}

	@Test
	public void testVisitorPersonalRepository() throws Exception {
		RepositoryModel repository = new RepositoryModel("~test/myrepo.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.VIEW;

		UserModel user = new UserModel("visitor");
		repository.addOwner("test");

		assertFalse("user HAS a repository permission!", user.hasRepositoryPermission(repository.name));
		assertFalse("user CAN view!", user.canView(repository));
		assertFalse("user CAN clone!", user.canClone(repository));
		assertFalse("user CAN push!", user.canPush(repository));

		assertFalse("user CAN create ref!", user.canCreateRef(repository));
		assertFalse("user CAN delete ref!", user.canDeleteRef(repository));
		assertFalse("user CAN rewind ref!", user.canRewindRef(repository));

		assertEquals("user has wrong permission!", AccessPermission.NONE, user.getRepositoryPermission(repository).permission);

		assertFalse("user CAN fork!", user.canFork(repository));

		assertFalse("user CAN delete!", user.canDelete(repository));
		assertFalse("user CAN edit!", user.canEdit(repository));
	}

	@Test
	public void testRegexMatching() throws Exception {
		RepositoryModel repository = new RepositoryModel("ubercool/_my-r/e~po.git", null, null, new Date());
		repository.authorizationControl = AuthorizationControl.NAMED;
		repository.accessRestriction = AccessRestrictionType.VIEW;

		UserModel user = new UserModel("test");
		user.setRepositoryPermission("ubercool/[A-Z0-9-~_\\./]+", AccessPermission.CLONE);

		assertTrue("user DOES NOT HAVE a repository permission!", user.hasRepositoryPermission(repository.name));
		assertTrue("user CAN NOT view!", user.canView(repository));
		assertTrue("user CAN NOT clone!", user.canClone(repository));
		assertFalse("user CAN push!", user.canPush(repository));

		assertFalse("user CAN create ref!", user.canCreateRef(repository));
		assertFalse("user CAN delete ref!", user.canDeleteRef(repository));
		assertFalse("user CAN rewind ref!", user.canRewindRef(repository));

		assertEquals("user has wrong permission!", AccessPermission.CLONE, user.getRepositoryPermission(repository).permission);

		assertFalse("user CAN fork!", user.canFork(repository));

		assertFalse("user CAN delete!", user.canDelete(repository));
		assertFalse("user CAN edit!", user.canEdit(repository));
	}

	@Test
	public void testRegexIncludeCommonExcludePersonal() throws Exception {

		UserModel user = new UserModel("test");
		user.setRepositoryPermission("[^~].*", AccessPermission.CLONE);

		// common
		RepositoryModel common = new RepositoryModel("ubercool/_my-r/e~po.git", null, null, new Date());
		common.authorizationControl = AuthorizationControl.NAMED;
		common.accessRestriction = AccessRestrictionType.VIEW;

		assertTrue("user DOES NOT HAVE a repository permission!", user.hasRepositoryPermission(common.name));
		assertTrue("user CAN NOT view!", user.canView(common));
		assertTrue("user CAN NOT clone!", user.canClone(common));
		assertFalse("user CAN push!", user.canPush(common));

		assertFalse("user CAN create ref!", user.canCreateRef(common));
		assertFalse("user CAN delete ref!", user.canDeleteRef(common));
		assertFalse("user CAN rewind ref!", user.canRewindRef(common));

		assertEquals("user has wrong permission!", AccessPermission.CLONE, user.getRepositoryPermission(common).permission);

		assertFalse("user CAN fork!", user.canFork(common));

		assertFalse("user CAN delete!", user.canDelete(common));
		assertFalse("user CAN edit!", user.canEdit(common));

		// personal
		RepositoryModel personal = new RepositoryModel("~ubercool/_my-r/e~po.git", null, null, new Date());
		personal.authorizationControl = AuthorizationControl.NAMED;
		personal.accessRestriction = AccessRestrictionType.VIEW;

		assertFalse("user HAS a repository permission!", user.hasRepositoryPermission(personal.name));
		assertFalse("user CAN NOT view!", user.canView(personal));
		assertFalse("user CAN NOT clone!", user.canClone(personal));
		assertFalse("user CAN push!", user.canPush(personal));

		assertFalse("user CAN create ref!", user.canCreateRef(personal));
		assertFalse("user CAN delete ref!", user.canDeleteRef(personal));
		assertFalse("user CAN rewind ref!", user.canRewindRef(personal));

		assertEquals("user has wrong permission!", AccessPermission.NONE, user.getRepositoryPermission(personal).permission);

		assertFalse("user CAN fork!", user.canFork(personal));

		assertFalse("user CAN delete!", user.canDelete(personal));
		assertFalse("user CAN edit!", user.canEdit(personal));
	}

	@Test
	public void testRegexMatching2() throws Exception {
		RepositoryModel personal = new RepositoryModel("~ubercool/_my-r/e~po.git", null, null, new Date());
		personal.authorizationControl = AuthorizationControl.NAMED;
		personal.accessRestriction = AccessRestrictionType.VIEW;

		UserModel user = new UserModel("test");
		// permit all repositories excluding all personal rpeositories
		user.setRepositoryPermission("[^~].*", AccessPermission.CLONE);
		// permitall  ~ubercool repositories
		user.setRepositoryPermission("~ubercool/.*", AccessPermission.CLONE);

		// personal
		assertTrue("user DOES NOT HAVE a repository permission!", user.hasRepositoryPermission(personal.name));
		assertTrue("user CAN NOT view!", user.canView(personal));
		assertTrue("user CAN NOT clone!", user.canClone(personal));
		assertFalse("user CAN push!", user.canPush(personal));

		assertFalse("user CAN create ref!", user.canCreateRef(personal));
		assertFalse("user CAN delete ref!", user.canDeleteRef(personal));
		assertFalse("user CAN rewind ref!", user.canRewindRef(personal));

		assertEquals("user has wrong permission!", AccessPermission.CLONE, user.getRepositoryPermission(personal).permission);

		assertFalse("user CAN fork!", user.canFork(personal));

		assertFalse("user CAN delete!", user.canDelete(personal));
		assertFalse("user CAN edit!", user.canEdit(personal));
	}

	@Test
	public void testRegexOrder() throws Exception {
		RepositoryModel personal = new RepositoryModel("~ubercool/_my-r/e~po.git", null, null, new Date());
		personal.authorizationControl = AuthorizationControl.NAMED;
		personal.accessRestriction = AccessRestrictionType.VIEW;

		UserModel user = new UserModel("test");
		user.setRepositoryPermission(".*", AccessPermission.PUSH);
		user.setRepositoryPermission("~ubercool/.*", AccessPermission.CLONE);

		// has PUSH access because first match is PUSH permission
		assertTrue("user HAS a repository permission!", user.hasRepositoryPermission(personal.name));
		assertTrue("user CAN NOT view!", user.canView(personal));
		assertTrue("user CAN NOT clone!", user.canClone(personal));
		assertTrue("user CAN NOT push!", user.canPush(personal));

		assertFalse("user CAN create ref!", user.canCreateRef(personal));
		assertFalse("user CAN delete ref!", user.canDeleteRef(personal));
		assertFalse("user CAN rewind ref!", user.canRewindRef(personal));

		assertEquals("user has wrong permission!", AccessPermission.PUSH, user.getRepositoryPermission(personal).permission);

		assertFalse("user CAN fork!", user.canFork(personal));

		assertFalse("user CAN delete!", user.canDelete(personal));
		assertFalse("user CAN edit!", user.canEdit(personal));

		user.permissions.clear();
		user.setRepositoryPermission("~ubercool/.*", AccessPermission.CLONE);
		user.setRepositoryPermission(".*", AccessPermission.PUSH);

		// has CLONE access because first match is CLONE permission
		assertTrue("user HAS a repository permission!", user.hasRepositoryPermission(personal.name));
		assertTrue("user CAN NOT view!", user.canView(personal));
		assertTrue("user CAN NOT clone!", user.canClone(personal));
		assertFalse("user CAN push!", user.canPush(personal));

		assertFalse("user CAN create ref!", user.canCreateRef(personal));
		assertFalse("user CAN delete ref!", user.canDeleteRef(personal));
		assertFalse("user CAN rewind ref!", user.canRewindRef(personal));

		assertEquals("user has wrong permission!", AccessPermission.CLONE, user.getRepositoryPermission(personal).permission);

		assertFalse("user CAN fork!", user.canFork(personal));

		assertFalse("user CAN delete!", user.canDelete(personal));
		assertFalse("user CAN edit!", user.canEdit(personal));
	}

	@Test
	public void testExclusion() throws Exception {
		RepositoryModel personal = new RepositoryModel("~ubercool/_my-r/e~po.git", null, null, new Date());
		personal.authorizationControl = AuthorizationControl.NAMED;
		personal.accessRestriction = AccessRestrictionType.VIEW;

		UserModel user = new UserModel("test");
		user.setRepositoryPermission("~ubercool/.*", AccessPermission.EXCLUDE);
		user.setRepositoryPermission(".*", AccessPermission.PUSH);

		// has EXCLUDE access because first match is EXCLUDE permission
		assertTrue("user DOES NOT HAVE a repository permission!", user.hasRepositoryPermission(personal.name));
		assertFalse("user CAN NOT view!", user.canView(personal));
		assertFalse("user CAN NOT clone!", user.canClone(personal));
		assertFalse("user CAN push!", user.canPush(personal));

		assertFalse("user CAN create ref!", user.canCreateRef(personal));
		assertFalse("user CAN delete ref!", user.canDeleteRef(personal));
		assertFalse("user CAN rewind ref!", user.canRewindRef(personal));

		assertEquals("user has wrong permission!", AccessPermission.EXCLUDE, user.getRepositoryPermission(personal).permission);

		assertFalse("user CAN fork!", user.canFork(personal));

		assertFalse("user CAN delete!", user.canDelete(personal));
		assertFalse("user CAN edit!", user.canEdit(personal));
	}

	@Test
	public void testAdminTeamInheritance() throws Exception {
		UserModel user = new UserModel("test");
		TeamModel team = new TeamModel("team");
		team.canAdmin = true;
		user.addTeam(team);
		assertTrue("User did not inherit admin privileges", user.canAdmin());
	}

	@Test
	public void testForkTeamInheritance() throws Exception {
		UserModel user = new UserModel("test");
		TeamModel team = new TeamModel("team");
		team.canFork = true;
		user.addTeam(team);
		assertTrue("User did not inherit fork privileges", user.canFork());
	}

	@Test
	public void testCreateTeamInheritance() throws Exception {
		UserModel user = new UserModel("test");
		TeamModel team = new TeamModel("team");
		team.canCreate= true;
    user.addTeam(team);
		assertTrue("User did not inherit create privileges", user.canCreate());
	}

	@Test
	public void testIsFrozen() throws Exception {
		RepositoryModel repo = new RepositoryModel("somerepo.git", null, null, new Date());
		repo.authorizationControl = AuthorizationControl.NAMED;
		repo.accessRestriction = AccessRestrictionType.NONE;

		UserModel user = new UserModel("test");
		TeamModel team = new TeamModel("team");

		assertEquals("user has wrong permission!", AccessPermission.REWIND, user.getRepositoryPermission(repo).permission);
		assertEquals("team has wrong permission!", AccessPermission.REWIND, team.getRepositoryPermission(repo).permission);

		// freeze repo
		repo.isFrozen = true;
		assertEquals("user has wrong permission!", AccessPermission.CLONE, user.getRepositoryPermission(repo).permission);
		assertEquals("team has wrong permission!", AccessPermission.CLONE, team.getRepositoryPermission(repo).permission);
	}

	@Test
	public void testIsBare() throws Exception {
		RepositoryModel repo = new RepositoryModel("somerepo.git", null, null, new Date());
		repo.authorizationControl = AuthorizationControl.NAMED;
		repo.accessRestriction = AccessRestrictionType.NONE;

		UserModel user = new UserModel("test");
		TeamModel team = new TeamModel("team");

		assertEquals("user has wrong permission!", AccessPermission.REWIND, user.getRepositoryPermission(repo).permission);
		assertEquals("team has wrong permission!", AccessPermission.REWIND, team.getRepositoryPermission(repo).permission);

		// set repo to have a working copy, pushes prohibited
		repo.isBare = false;
		assertEquals("user has wrong permission!", AccessPermission.CLONE, user.getRepositoryPermission(repo).permission);
		assertEquals("team has wrong permission!", AccessPermission.CLONE, team.getRepositoryPermission(repo).permission);
	}

	@Test
	public void testIsMirror() throws Exception {
		RepositoryModel repo = new RepositoryModel("somerepo.git", null, null, new Date());
		repo.authorizationControl = AuthorizationControl.NAMED;
		repo.accessRestriction = AccessRestrictionType.NONE;

		UserModel user = new UserModel("test");
		TeamModel team = new TeamModel("team");

		assertEquals("user has wrong permission!", AccessPermission.REWIND, user.getRepositoryPermission(repo).permission);
		assertEquals("team has wrong permission!", AccessPermission.REWIND, team.getRepositoryPermission(repo).permission);

		// set repo to be a mirror, pushes prohibited
		repo.isMirror = true;
		assertEquals("user has wrong permission!", AccessPermission.CLONE, user.getRepositoryPermission(repo).permission);
		assertEquals("team has wrong permission!", AccessPermission.CLONE, team.getRepositoryPermission(repo).permission);
	}
}
