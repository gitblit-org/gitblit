/*
 * Copyright 2013 gitblit.com.
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

import org.jvnet.libpam.PAM;
import org.jvnet.libpam.PAMException;
import org.jvnet.libpam.impl.CLibrary;

import com.gitblit.Constants;
import com.gitblit.Constants.AccountType;
import com.gitblit.Constants.Role;
import com.gitblit.Keys;
import com.gitblit.auth.AuthenticationProvider.UsernamePasswordAuthenticationProvider;
import com.gitblit.models.TeamModel;
import com.gitblit.models.UserModel;

/**
 * Implementation of PAM authentication for Linux/Unix/MacOSX.
 *
 * @author James Moger
 */
public class PAMAuthProvider extends UsernamePasswordAuthenticationProvider {

    public PAMAuthProvider() {
        super("pam");
    }

    @Override
    public void setup() {
        // Try to identify the passwd database
        String [] files = { "/etc/shadow", "/etc/master.passwd" };
		File passwdFile = null;
		for (String name : files) {
			File f = new File(name);
			if (f.exists()) {
				passwdFile = f;
				break;
			}
		}
		if (passwdFile == null) {
			logger.error("PAM Authentication could not find a passwd database!");
		} else if (!passwdFile.canRead()) {
			logger.error("PAM Authentication can not read passwd database {}! PAM authentications may fail!", passwdFile);
		}
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

	 @Override
	public AccountType getAccountType() {
		return AccountType.PAM;
	}

    @Override
    public UserModel authenticate(String username, char[] password) {
		if (CLibrary.libc.getpwnam(username) == null) {
			logger.warn("Can not get PAM passwd for " + username);
			return null;
		}

		PAM pam = null;
		try {
			String serviceName = settings.getString(Keys.realm.pam.serviceName, "system-auth");
			pam = new PAM(serviceName);
			pam.authenticate(username, new String(password));
		} catch (PAMException e) {
			logger.error(e.getMessage());
			return null;
		} finally {
			if (pam != null) {
				pam.dispose();
			}
		}

        UserModel user = userManager.getUserModel(username);
        if (user == null) {
        	// create user object for new authenticated user
        	user = new UserModel(username.toLowerCase());
        }

        // create a user cookie
        setCookie(user);

        // update user attributes from UnixUser
        user.accountType = getAccountType();
        user.password = Constants.EXTERNAL_ACCOUNT;

        // TODO consider mapping PAM groups to teams

        // push the changes to the backing user service
        updateUser(user);

        return user;
    }
}
