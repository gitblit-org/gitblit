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
package com.gitblit.transport.ssh.gitblit;

import com.gitblit.models.UserModel;
import com.gitblit.transport.ssh.commands.CommandMetaData;
import com.gitblit.transport.ssh.commands.DispatchCommand;

/**
 * The dispatcher and it's commands for Gitblit object listing.
 *
 * @author James Moger
 *
 */
@CommandMetaData(name = "list", aliases = { "ls" }, description = "Gitblit object list commands")
public class ListDispatcher extends DispatchCommand {

	@Override
	protected void setup(UserModel user) {
		register(user, ListRepositories.class);
		register(user, ListProjects.class);
		register(user, ListUsers.class);
		register(user, ListKeys.class);
	}

	/* List SSH public keys */
	@CommandMetaData(name = "keys",  description = "List your public keys")
	public static class ListKeys extends KeysDispatcher.ListKeys {
	}

	/* List repositories */
	@CommandMetaData(name = "repositories", aliases = { "repos" }, description = "List repositories")
	public static class ListRepositories extends RepositoriesDispatcher.ListRepositories {
	}

	/* List projects */
	@CommandMetaData(name = "projects", description = "List projects")
	public static class ListProjects extends ProjectsDispatcher.ListProjects {
	}

	/* List users */
	@CommandMetaData(name = "users", description = "List users", admin = true)
	public static class ListUsers extends UsersDispatcher.ListUsers {
	}
}
