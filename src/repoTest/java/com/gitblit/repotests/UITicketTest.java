/*
 * Copyright 2014 gitblit.com.
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
package com.gitblit.repotests;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jgit.util.FileUtils;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.manager.INotificationManager;
import com.gitblit.manager.IPluginManager;
import com.gitblit.manager.IRepositoryManager;
import com.gitblit.manager.IRuntimeManager;
import com.gitblit.manager.IUserManager;
import com.gitblit.manager.NotificationManager;
import com.gitblit.manager.PluginManager;
import com.gitblit.manager.RepositoryManager;
import com.gitblit.manager.RuntimeManager;
import com.gitblit.manager.UserManager;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.TicketModel;
import com.gitblit.models.TicketModel.Change;
import com.gitblit.models.TicketModel.Field;
import com.gitblit.models.TicketModel.Priority;
import com.gitblit.models.TicketModel.Severity;
import com.gitblit.models.TicketModel.Type;
import com.gitblit.tests.GitBlitTestConfig;
import com.gitblit.tests.mock.MemorySettings;
import com.gitblit.tickets.ITicketService;
import com.gitblit.tickets.BranchTicketService;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.XssFilter;
import com.gitblit.utils.XssFilter.AllowXssFilter;

/**
 * Generates the range of tickets to ease testing of the look and feel of tickets
 */
public class UITicketTest extends RepoUnitTest {

	private ITicketService service;	
	static final String repoName = "UITicketTest.git";
	final RepositoryModel repo = new RepositoryModel(repoName, null, null, null);
	
	protected ITicketService getService(boolean deleteAll) throws Exception {

		IStoredSettings settings = getSettings(deleteAll);
		XssFilter xssFilter = new AllowXssFilter();
		IRuntimeManager runtimeManager = new RuntimeManager(settings, xssFilter).start();
		IPluginManager pluginManager = new PluginManager(runtimeManager).start();
		INotificationManager notificationManager = new NotificationManager(settings).start();
		IUserManager userManager = new UserManager(runtimeManager, pluginManager).start();
		IRepositoryManager repositoryManager = new RepositoryManager(runtimeManager, pluginManager, userManager).start();

		BranchTicketService service = (BranchTicketService) new BranchTicketService(
				runtimeManager,
				pluginManager,
				notificationManager,
				userManager,
				repositoryManager).start();

		if (deleteAll) {
			service.deleteAll(repo);
		}
		return service;
	}
	
	protected IStoredSettings getSettings(boolean deleteAll) throws Exception {
		File dir = new File(GitBlitTestConfig.REPOSITORIES, repoName);
		if (deleteAll) {
			if (dir.exists()) {
				FileUtils.delete(dir, FileUtils.RECURSIVE | FileUtils.RETRY);
			}
			JGitUtils.createRepository(GitBlitTestConfig.REPOSITORIES, repoName).close();
		}
		
		File luceneDir = new File(dir, "tickets/lucene");
		luceneDir.mkdirs();

		Map<String, Object> map = new HashMap<String, Object>();
		map.put(Keys.git.repositoriesFolder, GitBlitTestConfig.REPOSITORIES.getAbsolutePath());
		map.put(Keys.tickets.indexFolder, luceneDir.getAbsolutePath());

		IStoredSettings settings = new MemorySettings(map);
		return settings;
	}

	@AfterClass
	public static void deleteUITicketTestRepo() throws IOException {
		//delete the UITicketTest.git folder, at end of the test
		File dir = new File(GitBlitTestConfig.REPOSITORIES, repoName);
		if (dir.exists()) {
			FileUtils.delete(dir, FileUtils.RECURSIVE | FileUtils.RETRY);
		}
	}

	@Before
	public void setup() throws Exception {
		service = getService(true);
	}

	@After
	public void cleanup() {
		service.stop();
	}

	@Test
	public void UITicketOptions() throws Exception {
		
		for (TicketModel.Type t : TicketModel.Type.values())
		{
			for (TicketModel.Priority p : TicketModel.Priority.values())
			{
				for (TicketModel.Severity s : TicketModel.Severity.values())
				{
					assertNotNull(service.createTicket(repo, newChange(t, p, s)));
				}
			}	
		}
	}
	
	private Change newChange(Type type, Priority priority, Severity severity) {
		Change change = new Change("JUnit");
		change.setField(Field.title, String.format("Type: %s | Priority: %s | Severity: %s", type, priority, severity));
		change.setField(Field.type, type);
		change.setField(Field.severity, severity);
		change.setField(Field.priority, priority);
		return change;
	}

}
