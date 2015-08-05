/*
 * Copyright (C) 2012 The Android Open Source Project
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

import java.io.IOException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.MessageFormat;

import org.apache.sshd.server.Environment;
import org.eclipse.jgit.util.SystemReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.manager.IGitblit;
import com.gitblit.utils.StringUtils;

public abstract class SshCommand extends BaseCommand {

	protected Logger log = LoggerFactory.getLogger(getClass());
	protected PrintWriter stdout;
	protected PrintWriter stderr;

	@Override
	public void start(Environment env) throws IOException {
		startThread(new CommandRunnable() {
			@Override
			public void run() throws Exception {
				parseCommandLine();
				stdout = toPrintWriter(out);
				stderr = toPrintWriter(err);
				try {
					SshCommand.this.run();
				} finally {
					stdout.flush();
					stderr.flush();
				}
			}
		});
	}

	protected String getHostname() {
		IGitblit gitblit = getContext().getGitblit();
		String host = null;
		String url = gitblit.getSettings().getString(Keys.web.canonicalUrl, "https://localhost:8443");
		if (url != null) {
			try {
				host = new URL(url).getHost();
			} catch (MalformedURLException e) {
			}
		}
		if (StringUtils.isEmpty(host)) {
			host = SystemReader.getInstance().getHostname();
		}
		return host;
	}

	protected String getRepositoryUrl(String repository) {
		String username = getContext().getClient().getUsername();
		IStoredSettings settings = getContext().getGitblit().getSettings();
		String displayHostname = settings.getString(Keys.git.sshAdvertisedHost, "");
		if(displayHostname.isEmpty()) {
			displayHostname = getHostname();
		}
		int port = settings.getInteger(Keys.git.sshPort, 0);
		int displayPort = settings.getInteger(Keys.git.sshAdvertisedPort, port);
		if (displayPort == 22) {
			// standard port
			return MessageFormat.format("{0}@{1}/{2}.git", username, displayHostname, repository);
		} else {
			// non-standard port
			return MessageFormat.format("ssh://{0}@{1}:{2,number,0}/{3}",
					username, displayHostname, displayPort, repository);
		}
	}

	protected abstract void run() throws Failure, Exception;
}
