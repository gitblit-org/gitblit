
package com.gitblit.transport.ssh;

import java.security.PublicKey;

class SshKeyCacheEntry {
  private final String user;
  private final PublicKey publicKey;

  SshKeyCacheEntry(String user, PublicKey publicKey) {
    this.user = user;
    this.publicKey = publicKey;
  }

  String getUser() {
    return user;
  }

  boolean match(PublicKey inkey) {
    return publicKey.equals(inkey);
  }

  int weigh() {
    return publicKey.getEncoded().length;
  }
}
