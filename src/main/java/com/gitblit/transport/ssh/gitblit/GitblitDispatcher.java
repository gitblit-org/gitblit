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

@CommandMetaData(name = "gitblit", description = "Gitblit server commands")
public class GitblitDispatcher extends DispatchCommand {

	@Override
	protected void setup(UserModel user) {
		// commands in this dispatcher
		register(user, VersionCommand.class);
		register(user, ConfigCommand.class);

		// nested dispatchers
		register(user, ListDispatcher.class);
		register(user, KeysDispatcher.class);
		register(user, TicketsDispatcher.class);
		register(user, UsersDispatcher.class);
		register(user, ProjectsDispatcher.class);
		register(user, RepositoriesDispatcher.class);
	}
}
