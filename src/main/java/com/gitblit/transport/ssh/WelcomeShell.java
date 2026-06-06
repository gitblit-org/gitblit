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
package com.gitblit.transport.ssh;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.MessageFormat;

import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.server.session.ServerSessionAware;
import org.apache.sshd.server.shell.ShellFactory;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.util.SystemReader;

import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.manager.IGitblit;
import com.gitblit.models.UserModel;
import com.gitblit.transport.ssh.commands.DispatchCommand;
import com.gitblit.transport.ssh.commands.SshCommandFactory;
import com.gitblit.utils.StringUtils;

public class WelcomeShell implements ShellFactory {

    private final IGitblit gitblit;

    public WelcomeShell(IGitblit gitblit) {
        this.gitblit = gitblit;
    }

    @Override
    public Command createShell(ChannelSession channel) throws IOException {
        return new SendMessage(gitblit);
    }

    private static class SendMessage implements Command, ServerSessionAware {

        private final IPublicKeyManager km;
        private final IStoredSettings settings;
        private ServerSession session;

        private InputStream in;
        private OutputStream out;
        private OutputStream err;
        private ExitCallback exit;

        SendMessage(IGitblit gitblit) {
            this.km = gitblit.getPublicKeyManager();
            this.settings = gitblit.getSettings();
        }

        @Override
        public void setInputStream(InputStream in) {
            this.in = in;
        }

        @Override
        public void setOutputStream(OutputStream out) {
            this.out = out;
        }

        @Override
        public void setErrorStream(OutputStream err) {
            this.err = err;
        }

        @Override
        public void setExitCallback(ExitCallback callback) {
            this.exit = callback;
        }

        @Override
        public void setSession(ServerSession session) {
            this.session = session;
        }

        @Override
        public void start(ChannelSession channel, Environment env) throws IOException {
            err.write(Constants.encode(getMessage()));
            err.flush();

            in.close();
            out.close();
            err.close();
            exit.onExit(127);
        }

        @Override
        public void destroy(ChannelSession channel) {
            this.session = null;
        }

        String getMessage() {
            SshDaemonClient client = SshDaemonClient.get(session);
            UserModel user = client.getUser();
            String hostname = getHostname();
            int port = settings.getInteger(Keys.git.sshPort, 0);
            boolean writeKeysIsSupported = true;
            if (km != null) {
                writeKeysIsSupported = km.supportsWritingKeys(user);
            }

            final String b1 = StringUtils.rightPad("", 72, '═');
            final String b2 = StringUtils.rightPad("", 72, '─');
            final String nl = "\r\n";

            StringBuilder msg = new StringBuilder();
            msg.append(nl);
            msg.append(b1);
            msg.append(nl);
            msg.append(" ");
            msg.append(com.gitblit.Constants.getGitBlitVersion());
            msg.append(nl);
            msg.append(b1);
            msg.append(nl);
            msg.append(nl);
            msg.append(" Hi ");
            msg.append(user.getDisplayName());
            msg.append(", you have successfully connected over SSH.");
            msg.append(nl);
            msg.append(" Interactive shells are not available.");
            msg.append(nl);
            msg.append(nl);
            msg.append("   client:   ");
            msg.append(session.getClientVersion());
            msg.append(nl);
            msg.append(nl);

            msg.append(b2);
            msg.append(nl);
            msg.append(nl);
            msg.append(" You may clone a repository with the following Git syntax:");
            msg.append(nl);
            msg.append(nl);

            msg.append("   git clone ");
            msg.append(formatUrl(hostname, port, user.username));
            msg.append(nl);
            msg.append(nl);

            msg.append(b2);
            msg.append(nl);
            msg.append(nl);

            if (writeKeysIsSupported && client.getKey() == null) {
                msg.append(" You may upload an SSH public key with the following syntax:");
                msg.append(nl);
                msg.append(nl);

                msg.append(String.format("   cat ~/.ssh/id_rsa.pub | ssh -l %s -p %d %s keys add", user.username, port, hostname));
                msg.append(nl);
                msg.append(nl);

                msg.append(b2);
                msg.append(nl);
                msg.append(nl);
            }

            SshCommandFactory cmdFactory = (SshCommandFactory) session.getFactoryManager().getCommandFactory();
            DispatchCommand root = cmdFactory.createRootDispatcher(client, "");
            String usage = root.usage().replace("\n", nl);
            msg.append(usage);

            return msg.toString();
        }

        private String getHostname() {
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
            return host;
        }

        private String formatUrl(String hostname, int port, String username) {
            int displayPort = settings.getInteger(Keys.git.sshAdvertisedPort, port);
            String displayHostname = settings.getString(Keys.git.sshAdvertisedHost, "");
            if (displayHostname.isEmpty()) {
                displayHostname = hostname;
            }
            if (displayPort == 22) {
                return MessageFormat.format("ssh://{0}@{1}/{2}", username, displayHostname, "${repository}");
            }
            return MessageFormat.format("ssh://{0}@{1}:{2,number,0}/{3}", username, displayHostname, displayPort, "${repository}");
        }
    }
}
