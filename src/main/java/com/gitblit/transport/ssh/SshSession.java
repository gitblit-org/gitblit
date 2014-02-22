/*
 * Copyright 2014 gitblit.com.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.gitblit.transport.ssh;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

import org.apache.sshd.common.Session.AttributeKey;

/**
 *
 * @author Eric Myrhe
 *
 */
public class SshSession {
  public static final AttributeKey<SshSession> KEY =
      new AttributeKey<SshSession>();

  private final int sessionId;
  private final SocketAddress remoteAddress;
  private final String remoteAsString;

  private volatile String username;
  private volatile String authError;
  private volatile String repositoryName;

  SshSession(int sessionId, SocketAddress peer) {
    this.sessionId = sessionId;
    this.remoteAddress = peer;
    this.remoteAsString = format(remoteAddress);
  }

  public SocketAddress getRemoteAddress() {
    return remoteAddress;
  }

  String getRemoteAddressAsString() {
    return remoteAsString;
  }

  public String getRemoteUser() {
    return username;
  }

  /** Unique session number, assigned during connect. */
  public int getSessionId() {
    return sessionId;
  }

  String getUsername() {
    return username;
  }

  String getAuthenticationError() {
    return authError;
  }

  void authenticationSuccess(String user) {
    username = user;
    authError = null;
  }

  void authenticationError(String user, String error) {
    username = user;
    authError = error;
  }

  public void setRepositoryName(String repositoryName) {
	this.repositoryName = repositoryName;
  }

  public String getRepositoryName() {
	return repositoryName;
  }

  /** @return {@code true} if the authentication did not succeed. */
  boolean isAuthenticationError() {
    return authError != null;
  }

  private static String format(final SocketAddress remote) {
    if (remote instanceof InetSocketAddress) {
      final InetSocketAddress sa = (InetSocketAddress) remote;

      final InetAddress in = sa.getAddress();
      if (in != null) {
        return in.getHostAddress();
      }

      final String hostName = sa.getHostName();
      if (hostName != null) {
        return hostName;
      }
    }
    return remote.toString();
  }
}
