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
package com.gitblit.tests;

import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.manager.INotificationManager;
import com.gitblit.manager.IRepositoryManager;
import com.gitblit.manager.IRuntimeManager;
import com.gitblit.manager.IUserManager;
import com.gitblit.manager.NotificationManager;
import com.gitblit.manager.RepositoryManager;
import com.gitblit.manager.RuntimeManager;
import com.gitblit.manager.UserManager;
import com.gitblit.models.RepositoryModel;
import com.gitblit.tickets.ITicketService;
import com.gitblit.tickets.RedisTicketService;

/**
 * Tests the Redis ticket service.
 *
 * @author James Moger
 *
 */
public class RedisTicketServiceTest extends TicketServiceTest {

	final RepositoryModel repo = new RepositoryModel("tickets/redis.git", null, null, null);

	@Override
	protected RepositoryModel getRepository() {
		return repo;
	}

	@Override
	protected IStoredSettings getSettings(boolean deleteAll) throws Exception {
		IStoredSettings settings = super.getSettings(deleteAll);
		settings.overrideSetting(Keys.tickets.redis.url, "redis://localhost:6379/10");
		return settings;
	}

	@Override
	protected ITicketService getService(boolean deleteAll) throws Exception {

		IStoredSettings settings = getSettings(deleteAll);

		IRuntimeManager runtimeManager = new RuntimeManager(settings).start();
		INotificationManager notificationManager = new NotificationManager(settings).start();
		IUserManager userManager = new UserManager(runtimeManager).start();
		IRepositoryManager repositoryManager = new RepositoryManager(runtimeManager, userManager).start();

		RedisTicketService service = new RedisTicketService(
				runtimeManager,
				notificationManager,
				userManager,
				repositoryManager).start();

		if (deleteAll) {
			service.deleteAll(getRepository());
		}
		return service;
	}
}
