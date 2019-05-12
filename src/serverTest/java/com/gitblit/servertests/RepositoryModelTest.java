/*
 * Copyright 2012 John Crygier
 * Copyright 2012 gitblit.com
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
package com.gitblit.servertests;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.gitblit.Constants;
import com.gitblit.models.RepositoryModel;
import com.gitblit.tests.GitBlitTestConfig;

public class RepositoryModelTest extends GitblitUnitTest {

	private static boolean wasStarted = false;

	@BeforeClass
	public static void startGitBlit() throws Exception {
		wasStarted = GitBlitSuite.startGitblit() == false;
	}

	@AfterClass
	public static void stopGitBlit() throws Exception {
		if (wasStarted == false)
			GitBlitSuite.stopGitblit();
	}

	@Before
	public void initializeConfiguration() throws Exception{
		Repository r = GitBlitTestConfig.getHelloworldRepository();
		StoredConfig config = r.getConfig();

		config.unsetSection(Constants.CONFIG_GITBLIT, Constants.CONFIG_CUSTOM_FIELDS);
		config.setString(Constants.CONFIG_GITBLIT, Constants.CONFIG_CUSTOM_FIELDS, "commitMessageRegEx", "\\d");
		config.setString(Constants.CONFIG_GITBLIT, Constants.CONFIG_CUSTOM_FIELDS, "anotherProperty", "Hello");

		config.save();
	}

	@After
	public void teardownConfiguration() throws Exception {
		Repository r = GitBlitTestConfig.getHelloworldRepository();
		StoredConfig config = r.getConfig();

		config.unsetSection(Constants.CONFIG_GITBLIT, Constants.CONFIG_CUSTOM_FIELDS);
		config.save();
	}

	@Test
	public void testGetCustomProperty() throws Exception {
		RepositoryModel model = repositories().getRepositoryModel(
				GitBlitTestConfig.getHelloworldRepository().getDirectory().getName());

		assertEquals("\\d", model.customFields.get("commitMessageRegEx"));
		assertEquals("Hello", model.customFields.get("anotherProperty"));
	}

	@Test
	public void testSetCustomProperty() throws Exception {
		RepositoryModel model = repositories().getRepositoryModel(
				GitBlitTestConfig.getHelloworldRepository().getDirectory().getName());

		assertEquals("\\d", model.customFields.get("commitMessageRegEx"));
		assertEquals("Hello", model.customFields.get("anotherProperty"));

		assertEquals("Hello", model.customFields.put("anotherProperty", "GoodBye"));
		repositories().updateRepositoryModel(model.name, model, false);

		model = repositories().getRepositoryModel(
				GitBlitTestConfig.getHelloworldRepository().getDirectory().getName());

		assertEquals("\\d", model.customFields.get("commitMessageRegEx"));
		assertEquals("GoodBye", model.customFields.get("anotherProperty"));
	}

}
