/*
 * Copyright 2013 Florian Zschocke
 * Copyright 2013 gitblit.com
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
package com.gitblit.auth;

import java.io.File;
import java.io.FileInputStream;
import java.text.MessageFormat;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.Crypt;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.codec.digest.Md5Crypt;

import com.gitblit.Constants;
import com.gitblit.Constants.AccountType;
import com.gitblit.Constants.Role;
import com.gitblit.Keys;
import com.gitblit.auth.AuthenticationProvider.UsernamePasswordAuthenticationProvider;
import com.gitblit.models.TeamModel;
import com.gitblit.models.UserModel;


/**
 * Implementation of a user service using an Apache htpasswd file for authentication.
 *
 * This user service implement custom authentication using entries in a file created
 * by the 'htpasswd' program of an Apache web server. All possible output
 * options of the 'htpasswd' program version 2.2 are supported:
 * plain text (only on Windows and Netware),
 * glibc crypt() (not on Windows and NetWare),
 * Apache MD5 (apr1),
 * unsalted SHA-1.
 *
 * Configuration options:
 * realm.htpasswd.backingUserService - Specify the backing user service that is used
 *                                     to keep the user data other than the password.
 *                                     The default is '${baseFolder}/users.conf'.
 * realm.htpasswd.userfile - The text file with the htpasswd entries to be used for
 *                           authentication.
 *                           The default is '${baseFolder}/htpasswd'.
 * realm.htpasswd.overrideLocalAuthentication - Specify if local accounts are overwritten
 *                                              when authentication matches for an
 *                                              external account.
 *
 * @author Florian Zschocke
 *
 */
public class HtpasswdAuthProvider extends UsernamePasswordAuthenticationProvider {

    private static final String KEY_HTPASSWD_FILE = Keys.realm.htpasswd.userfile;
    private static final String DEFAULT_HTPASSWD_FILE = "${baseFolder}/htpasswd";

    private static final String KEY_SUPPORT_PLAINTEXT_PWD = "realm.htpasswd.supportPlaintextPasswords";

    private boolean supportPlainTextPwd;

    private File htpasswdFile;

    private final Map<String, String> htUsers = new ConcurrentHashMap<String, String>();

    private volatile long lastModified;

    public HtpasswdAuthProvider() {
        super("htpasswd");
    }

    /**
     * Setup the user service.
     *
     * The HtpasswdUserService extends the GitblitUserService and is thus
     * backed by the available user services provided by the GitblitUserService.
     * In addition the setup tries to read and parse the htpasswd file to be used
     * for authentication.
     *
     * @param settings
     * @since 0.7.0
     */
    @Override
    public void setup() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.startsWith("windows") || os.startsWith("netware")) {
            supportPlainTextPwd = true;
        } else {
            supportPlainTextPwd = false;
        }
        read();
        logger.debug("Read " + htUsers.size() + " users from htpasswd file: " + this.htpasswdFile);
    }

    @Override
    public boolean supportsCredentialChanges() {
        return false;
    }

    @Override
    public boolean supportsDisplayNameChanges() {
        return true;
    }

    @Override
    public boolean supportsEmailAddressChanges() {
        return true;
    }

    @Override
    public boolean supportsTeamMembershipChanges() {
        return true;
    }

    @Override
    public boolean supportsRoleChanges(UserModel user, Role role) {
        return true;
    }

	@Override
	public boolean supportsRoleChanges(TeamModel team, Role role) {
		return true;
	}

    /**
     * Authenticate a user based on a username and password.
     *
     * If the account is determined to be a local account, authentication
     * will be done against the locally stored password.
     * Otherwise, the configured htpasswd file is read. All current output options
     * of htpasswd are supported: clear text, crypt(), Apache MD5 and unsalted SHA-1.
     *
     * @param username
     * @param password
     * @return a user object or null
     */
    @Override
    public UserModel authenticate(String username, char[] password) {
        read();
        String storedPwd = htUsers.get(username);
        if (storedPwd != null) {
            boolean authenticated = false;
            final String passwd = new String(password);

            // test Apache MD5 variant encrypted password
            if (storedPwd.startsWith("$apr1$")) {
                if (storedPwd.equals(Md5Crypt.apr1Crypt(passwd, storedPwd))) {
                    logger.debug("Apache MD5 encoded password matched for user '" + username + "'");
                    authenticated = true;
                }
            }
            // test unsalted SHA password
            else if (storedPwd.startsWith("{SHA}")) {
                String passwd64 = Base64.encodeBase64String(DigestUtils.sha1(passwd));
                if (storedPwd.substring("{SHA}".length()).equals(passwd64)) {
                    logger.debug("Unsalted SHA-1 encoded password matched for user '" + username + "'");
                    authenticated = true;
                }
            }
            // test libc crypt() encoded password
            else if (supportCryptPwd() && storedPwd.equals(Crypt.crypt(passwd, storedPwd))) {
                logger.debug("Libc crypt encoded password matched for user '" + username + "'");
                authenticated = true;
            }
            // test clear text
            else if (supportPlaintextPwd() && storedPwd.equals(passwd)){
                logger.debug("Clear text password matched for user '" + username + "'");
                authenticated = true;
            }


            if (authenticated) {
                logger.debug("Htpasswd authenticated: " + username);

                UserModel curr = userManager.getUserModel(username);
                UserModel user;
                if (curr == null) {
                    // create user object for new authenticated user
                    user = new UserModel(username);
                } else {
                	user = curr;
                }

                // create a user cookie
                setCookie(user, password);

                // Set user attributes, hide password from backing user service.
                user.password = Constants.EXTERNAL_ACCOUNT;
                user.accountType = getAccountType();

                // Push the looked up values to backing file
               	updateUser(user);

                return user;
            }
        }

        return null;
    }

    /**
     * Get the account type used for this user service.
     *
     * @return AccountType.HTPASSWD
     */
    @Override
	public AccountType getAccountType() {
        return AccountType.HTPASSWD;
    }

    /**
     * Reads the realm file and rebuilds the in-memory lookup tables.
     */
    protected synchronized void read() {
    	boolean forceReload = false;
    	File file = getFileOrFolder(KEY_HTPASSWD_FILE, DEFAULT_HTPASSWD_FILE);
        if (!file.equals(htpasswdFile)) {
            this.htpasswdFile = file;
            this.htUsers.clear();
            forceReload = true;
        }

        if (htpasswdFile.exists() && (forceReload || (htpasswdFile.lastModified() != lastModified))) {
            lastModified = htpasswdFile.lastModified();
            htUsers.clear();

            Pattern entry = Pattern.compile("^([^:]+):(.+)");

            Scanner scanner = null;
            try {
                scanner = new Scanner(new FileInputStream(htpasswdFile));
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine().trim();
                    if (!line.isEmpty() &&  !line.startsWith("#")) {
                        Matcher m = entry.matcher(line);
                        if (m.matches()) {
                            htUsers.put(m.group(1), m.group(2));
                        }
                    }
                }
            } catch (Exception e) {
                logger.error(MessageFormat.format("Failed to read {0}", htpasswdFile), e);
            } finally {
                if (scanner != null) {
                	scanner.close();
                }
            }
        }
    }

    private boolean supportPlaintextPwd() {
        return this.settings.getBoolean(KEY_SUPPORT_PLAINTEXT_PWD, supportPlainTextPwd);
    }

    private boolean supportCryptPwd() {
        return !supportPlaintextPwd();
    }

    /*
     * Method only used for unit tests. Return number of users read from htpasswd file.
     */
    public int getNumberHtpasswdUsers() {
        return this.htUsers.size();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + ((htpasswdFile != null) ? htpasswdFile.getAbsolutePath() : "null") + ")";
    }
}
