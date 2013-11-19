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
package com.gitblit;

import java.io.File;

import org.jvnet.libpam.PAM;
import org.jvnet.libpam.PAMException;
import org.jvnet.libpam.impl.CLibrary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.Constants.AccountType;
import com.gitblit.manager.IRuntimeManager;
import com.gitblit.models.UserModel;
import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.StringUtils;

/**
 * Implementation of a PAM user service for Linux/Unix/MacOSX.
 *
 * @author James Moger
 */
public class PAMUserService extends GitblitUserService {

    private final Logger logger = LoggerFactory.getLogger(PAMUserService.class);

    private IStoredSettings settings;

    public PAMUserService() {
        super();
    }

    @Override
    public void setup(IRuntimeManager runtimeManager) {
        this.settings = runtimeManager.getSettings();

        String file = settings.getString(Keys.realm.pam.backingUserService, "${baseFolder}/users.conf");
        File realmFile = runtimeManager.getFileOrFolder(file);

        serviceImpl = createUserService(realmFile);
        logger.info("PAM User Service backed by " + serviceImpl.toString());

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
			logger.error("PAM User Service could not find a passwd database!");
		} else if (!passwdFile.canRead()) {
			logger.error("PAM User Service can not read passwd database {}! PAM authentications may fail!", passwdFile);
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
	protected AccountType getAccountType() {
		return AccountType.PAM;
	}

    @Override
    public UserModel authenticate(String username, char[] password) {
		if (isLocalAccount(username)) {
			// local account, bypass PAM authentication
			return super.authenticate(username, password);
		}

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
			pam.dispose();
		}

        UserModel user = getUserModel(username);
        if (user == null)	// create user object for new authenticated user
        	user = new UserModel(username.toLowerCase());

        // create a user cookie
        if (StringUtils.isEmpty(user.cookie) && !ArrayUtils.isEmpty(password)) {
        	user.cookie = StringUtils.getSHA1(user.username + new String(password));
        }

        // update user attributes from UnixUser
        user.accountType = getAccountType();
        user.password = Constants.EXTERNAL_ACCOUNT;

        // TODO consider mapping PAM groups to teams

        // push the changes to the backing user service
        super.updateUserModel(user);

        return user;
    }
}
