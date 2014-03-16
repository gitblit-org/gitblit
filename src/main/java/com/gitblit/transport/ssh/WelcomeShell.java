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
package com.gitblit.transport.ssh;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.MessageFormat;

import org.apache.sshd.common.Factory;
import org.apache.sshd.server.Command;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.SessionAware;
import org.apache.sshd.server.session.ServerSession;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.util.SystemReader;

import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.models.UserModel;
import com.gitblit.utils.StringUtils;

/**
 * Class that displays a welcome message for any shell requests.
 *
 */
public class WelcomeShell implements Factory<Command> {

	private final IStoredSettings settings;

	public WelcomeShell(IStoredSettings settings) {
		this.settings = settings;
	}

	@Override
	public Command create() {
		return new SendMessage(settings);
	}

	private static class SendMessage implements Command, SessionAware {

		private final IStoredSettings settings;
		private SshDaemonClient client;

		private InputStream in;
		private OutputStream out;
		private OutputStream err;
		private ExitCallback exit;

		SendMessage(IStoredSettings settings) {
			this.settings = settings;
		}

		@Override
		public void setInputStream(final InputStream in) {
			this.in = in;
		}

		@Override
		public void setOutputStream(final OutputStream out) {
			this.out = out;
		}

		@Override
		public void setErrorStream(final OutputStream err) {
			this.err = err;
		}

		@Override
		public void setExitCallback(final ExitCallback callback) {
			this.exit = callback;
		}

		@Override
		public void setSession(final ServerSession session) {
			this.client = session.getAttribute(SshDaemonClient.KEY);
		}

		@Override
		public void start(final Environment env) throws IOException {
			err.write(Constants.encode(getMessage()));
			err.flush();

			in.close();
			out.close();
			err.close();
			exit.onExit(127);
		}

		@Override
		public void destroy() {
		}

		String getMessage() {
			UserModel user = client.getUser();

			StringBuilder msg = new StringBuilder();
			msg.append("\r\n");
			msg.append("  Hi ");
			msg.append(user.getDisplayName());
			msg.append(", you have successfully connected to Gitblit over SSH.");
			msg.append("\r\n");
			msg.append("\r\n");

			msg.append("  You may clone a repository with the following Git syntax:\r\n");
			msg.append("\r\n");

			msg.append("  git clone ");
			msg.append(formatUrl(user.username));
			msg.append("\r\n");
			msg.append("\r\n");

			return msg.toString();
		}

		private String formatUrl(String username) {
			String host = null;
			String url = settings.getString(Keys.web.canonicalUrl, "https://localhost:8443");
			if (url != null) {
				try {
					host = new URL(url).getHost();
				} catch (MalformedURLException e) {
				}
			}
			if (StringUtils.isEmpty(host)) {
				host = SystemReader.getInstance().getHostname();
			}

			int port = settings.getInteger(Keys.git.sshPort, 0);
			if (port == 22) {
				// standard port
				return MessageFormat.format("{0}@{1}/REPOSITORY.git", username, host);
			} else {
				// non-standard port
				return MessageFormat.format("ssh://{0}@{1}:{2,number,0}/REPOSITORY.git",
						username, host, port);
			}
		}
	}
}
