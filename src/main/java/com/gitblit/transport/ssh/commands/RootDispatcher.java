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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
@CommandMetaData(name = "")
class RootDispatcher extends DispatchCommand {

	private Logger log = LoggerFactory.getLogger(getClass());

	public RootDispatcher(IGitblit gitblit, SshDaemonClient client, String cmdLine) {
		super();
		setContext(new SshCommandContext(gitblit, client, cmdLine));

		UserModel user = client.getUser();
		register(user, GitblitDispatcher.class);
		register(user, GitDispatcher.class);

		List<DispatchCommand> exts = gitblit.getExtensions(DispatchCommand.class);
		for (DispatchCommand ext : exts) {
			Class<? extends DispatchCommand> extClass = ext.getClass();
			String plugin = gitblit.whichPlugin(extClass).getDescriptor().getPluginId();
			CommandMetaData meta = extClass.getAnnotation(CommandMetaData.class);
			log.info("Dispatcher {} is loaded from plugin {}", meta.name(), plugin);
			register(user, ext);
		}
	}

	@Override
	protected final void setup(UserModel user) {
	}
}