/*
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
package com.gitblit.utils;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.Crypt;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.codec.digest.Md5Crypt;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Collection of password utilities.
 * 
 * @author Glenn Matthys <glenn@webmind.be>
 */
public class PasswordUtils {

    protected static final Logger logger = LoggerFactory.getLogger(PasswordUtils.class);

    public static final boolean apacheSupportsPlaintext = apacheSupportsPlaintext();
    public static final boolean apacheSupportsCrypt = !apacheSupportsPlaintext;

    /**
     * Check if a password is valid, with Apache htpasswd encryption formats.
     * The following algorithms are supported:
     * <ul>
     * <li>MD5</li>
     * <li>SHA-1 (unsalted)</li>
     * <li>Bcrypt</li>
     * <li>Plaintext if running on Windows</li>
     * </ul>
     * 
     * @param challenge Challenge password
     * @param stored The stored password that the challenge will be checked
     *            against
     * @param username Username involved (for logging purposes)
     * @return True if valid, false otherwise
     */
    public static boolean isApachePassValid(String challenge, String stored, String username) {
        if (stored.startsWith("$apr1$")) {
            if (stored.equals(Md5Crypt.apr1Crypt(challenge, stored))) {
                logger.debug("Apache MD5 encoded password matched for user '{}'", username);
                return true;
            }
        } else if (stored.startsWith("{SHA}")) {
            String passwd64 = Base64.encodeBase64String(DigestUtils.sha1(challenge));
            if (stored.substring("{SHA}".length()).equals(passwd64)) {
                logger.debug("Unsalted SHA-1 encoded password matched for user '{}'", username);
                return true;
            }
        } else if (stored.startsWith("$2y$")) {
            // We replace the $2y$ with $2a$ to validate using the BCrypt
            // library. See
            // http://stackoverflow.com/questions/27418597/bcrypt-version-1-1-2y-for-java
            if (BCrypt.checkpw(challenge, "$2a$" + stored.substring(4, stored.length()))) {
                logger.debug("BCrypt encoded password matched for user '{}'", username);
                return true;
            }
        } else if (apacheSupportsCrypt && stored.equals(Crypt.crypt(challenge, stored))) {
            logger.debug("Libc crypt encoded password matched for user '{}'", username);
            return true;
        } else if (apacheSupportsPlaintext && stored.equals(challenge)) {
            logger.warn("Clear text password matched for user '{}'", username);
            return true;
        }

        return false;
    }

    /**
     * Check whether or not the platform we're running on supports plaintext
     * authentication.
     * 
     * @return True if supported, false otherwise
     */
    public static boolean apacheSupportsPlaintext() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.startsWith("windows") || os.startsWith("netware")) {
            return true;
        } else {
            return false;
        }
    }

}
