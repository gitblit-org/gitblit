/*
 * Copyright (C) 2009 The Android Open Source Project
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

import com.gitblit.manager.IGitblit;
import com.gitblit.transport.ssh.SshDaemonClient;

public class SshCommandContext {

	private final IGitblit gitblit;
	private final SshDaemonClient client;
	private final String commandLine;

	public SshCommandContext(IGitblit gitblit, SshDaemonClient client, String commandLine) {
		this.gitblit = gitblit;
		this.client = client;
		this.commandLine = commandLine;
	}

	public IGitblit getGitblit() {
		return gitblit;
	}

	public SshDaemonClient getClient() {
		return client;
	}

	public String getCommandLine() {
		return commandLine;
	}
}
