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
import com.gitblit.transport.ssh.CommandMetaData;
import com.gitblit.transport.ssh.commands.DispatchCommand;

@CommandMetaData(name = "gitblit", description = "Gitblit server commands")
public class GitblitDispatchCommand extends DispatchCommand {

	@Override
	protected void registerCommands(UserModel user) {
		// normal usage commands
		registerCommand(user, VersionCommand.class);
		registerCommand(user, AddKeyCommand.class);
		registerCommand(user, RemoveKeyCommand.class);
		registerCommand(user, LsCommand.class);
		registerCommand(user, ReviewCommand.class);

		// administrative commands
		registerCommand(user, LsUsersCommand.class);
		registerCommand(user, CreateRepository.class);
		registerCommand(user, SetAccountCommand.class);
	}
}
