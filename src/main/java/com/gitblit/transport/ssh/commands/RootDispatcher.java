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
package com.gitblit.transport.ssh.commands;

import java.util.List;

import ro.fortsoft.pf4j.PluginWrapper;

import com.gitblit.manager.IGitblit;
import com.gitblit.models.UserModel;
import com.gitblit.transport.ssh.SshDaemonClient;
import com.gitblit.transport.ssh.git.GitDispatcher;
import com.gitblit.transport.ssh.gitblit.GitblitDispatcher;

/**
 * The root dispatcher is the dispatch command that handles registering all
 * other commands.
 *
 */
public class RootDispatcher extends DispatchCommand {

	public RootDispatcher(IGitblit gitblit, SshDaemonClient client, String cmdLine) {
		super();
		setContext(new SshCommandContext(gitblit, client, cmdLine));

		final UserModel user = client.getUser();
		registerDispatcher(user, GitblitDispatcher.class);
		registerDispatcher(user, GitDispatcher.class);

		List<DispatchCommand> p = gitblit.getExtensions(DispatchCommand.class);
		for (DispatchCommand d : p) {
			registerDispatcher(user, d.getClass());
		}
	}

	@Override
	protected final void registerCommands(UserModel user) {
	}

	@Override
	protected final void registerCommand(UserModel user, Class<? extends BaseCommand> cmd) {
		throw new RuntimeException("The root dispatcher does not accept commands, only dispatchers!");
	}
}