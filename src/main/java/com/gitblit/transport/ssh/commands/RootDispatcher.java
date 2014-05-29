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

import ro.fortsoft.pf4j.PluginWrapper;

import com.gitblit.manager.IGitblit;
import com.gitblit.transport.ssh.SshDaemonClient;
import com.gitblit.transport.ssh.git.GitDispatcher;
import com.gitblit.transport.ssh.keys.KeysDispatcher;
import com.gitblit.utils.WorkQueue;

/**
 * The root dispatcher is the dispatch command that handles registering all
 * other commands.
 *
 */
@CommandMetaData(name = "")
class RootDispatcher extends DispatchCommand {

	private Logger log = LoggerFactory.getLogger(getClass());

	public RootDispatcher(IGitblit gitblit, SshDaemonClient client, String cmdLine, WorkQueue workQueue) {
		super();
		setContext(new SshCommandContext(gitblit, client, cmdLine));
		setWorkQueue(workQueue);

		register(VersionCommand.class);
		register(GitDispatcher.class);
		register(KeysDispatcher.class);
		register(PluginDispatcher.class);

		List<DispatchCommand> exts = gitblit.getExtensions(DispatchCommand.class);
		for (DispatchCommand ext : exts) {
			Class<? extends DispatchCommand> extClass = ext.getClass();
			PluginWrapper wrapper = gitblit.whichPlugin(extClass);
			String plugin = wrapper.getDescriptor().getPluginId();
			CommandMetaData meta = extClass.getAnnotation(CommandMetaData.class);
			log.debug("Dispatcher {} is loaded from plugin {}", meta.name(), plugin);
			register(ext);
		}
	}

	@Override
	protected final void setup() {
	}
}