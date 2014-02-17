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

import java.io.File;
import java.io.IOException;
import java.security.PublicKey;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

import org.apache.commons.codec.binary.Base64;
import org.apache.sshd.common.util.Buffer;
import org.apache.sshd.server.PublickeyAuthenticator;
import org.apache.sshd.server.session.ServerSession;
import org.eclipse.jgit.lib.Constants;

import com.gitblit.manager.IGitblit;
import com.google.common.base.Charsets;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.Weigher;
import com.google.common.io.Files;

/**
 *
 * @author Eric Myrhe
 *
 */
public class SshKeyAuthenticator implements PublickeyAuthenticator {

  protected final IGitblit gitblit;

  LoadingCache<String, SshKeyCacheEntry> sshKeyCache = CacheBuilder
      .newBuilder().maximumWeight(2 << 20).weigher(new SshKeyCacheWeigher())
      .build(new CacheLoader<String, SshKeyCacheEntry>() {
        public SshKeyCacheEntry load(String key) throws Exception {
          return loadKey(key);
        }

        private SshKeyCacheEntry loadKey(String key) {
          try {
            // TODO(davido): retrieve absolute path to public key directory:
            //String dir = gitblit.getSettings().getString("public_key_dir", "data/ssh");
            String dir = "/tmp/";
            // Expect public key file name in form: <username.pub> in
            File file = new File(dir + key + ".pub");
            String str = Files.toString(file, Charsets.ISO_8859_1);
            final String[] parts = str.split(" ");
            final byte[] bin =
                Base64.decodeBase64(Constants.encodeASCII(parts[1]));
            return new SshKeyCacheEntry(key, new Buffer(bin).getRawPublicKey());
          } catch (IOException e) {
            throw new RuntimeException("Canot read public key", e);
          }
        }
      });

  public SshKeyAuthenticator(IGitblit gitblit) {
    this.gitblit = gitblit;
  }

  @Override
  public boolean authenticate(String username, final PublicKey suppliedKey,
      final ServerSession session) {
    final SshSession sd = session.getAttribute(SshSession.KEY);

    // if (config.getBoolean("auth", "userNameToLowerCase", false)) {
    username = username.toLowerCase(Locale.US);
    // }
    try {
      // TODO: allow multiple public keys per user
      SshKeyCacheEntry key = sshKeyCache.get(username);
      if (key == null) {
        sd.authenticationError(username, "no-matching-key");
        return false;
      }

      if (key.match(suppliedKey)) {
        return success(username, session, sd);
      }
      return false;
    } catch (ExecutionException e) {
      sd.authenticationError(username, "user-not-found");
      return false;
    }
  }

  boolean success(String username, ServerSession session, SshSession sd) {
    sd.authenticationSuccess(username);
    /*
     * sshLog.onLogin();
     *
     * GerritServerSession s = (GerritServerSession) session;
     * s.addCloseSessionListener( new SshFutureListener<CloseFuture>() {
     *
     * @Override public void operationComplete(CloseFuture future) { final
     * Context ctx = sshScope.newContext(null, sd, null); final Context old =
     * sshScope.set(ctx); try { sshLog.onLogout(); } finally {
     * sshScope.set(old); } } }); }
     */
    return true;
  }

  private static class SshKeyCacheWeigher implements
      Weigher<String, SshKeyCacheEntry> {
    @Override
    public int weigh(String key, SshKeyCacheEntry value) {
      return key.length() + value.weigh();
    }
  }
}
