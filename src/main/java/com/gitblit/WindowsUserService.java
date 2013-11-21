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
import java.util.Set;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import waffle.windows.auth.IWindowsAccount;
import waffle.windows.auth.IWindowsAuthProvider;
import waffle.windows.auth.IWindowsComputer;
import waffle.windows.auth.IWindowsIdentity;
import waffle.windows.auth.impl.WindowsAuthProviderImpl;

import com.gitblit.Constants.AccountType;
import com.gitblit.manager.IRuntimeManager;
import com.gitblit.models.UserModel;
import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.StringUtils;
import com.sun.jna.platform.win32.Win32Exception;

/**
 * Implementation of a Windows user service.
 *
 * @author James Moger
 */
public class WindowsUserService extends GitblitUserService {

    private final Logger logger = LoggerFactory.getLogger(WindowsUserService.class);

    private IStoredSettings settings;

    private IWindowsAuthProvider waffle;

    public WindowsUserService() {
        super();
    }

    @Override
    public void setup(IRuntimeManager runtimeManager) {
        this.settings = runtimeManager.getSettings();

        String file = settings.getString(Keys.realm.windows.backingUserService, "${baseFolder}/users.conf");
        File realmFile = runtimeManager.getFileOrFolder(file);

        serviceImpl = createUserService(realmFile);
        logger.info("Windows User Service backed by " + serviceImpl.toString());

        waffle = new WindowsAuthProviderImpl();
        IWindowsComputer computer = waffle.getCurrentComputer();
        logger.info("      name = " + computer.getComputerName());
        logger.info("    status = " + describeJoinStatus(computer.getJoinStatus()));
        logger.info("  memberOf = " + computer.getMemberOf());
        //logger.info("  groups     = " + Arrays.asList(computer.getGroups()));
    }

    protected String describeJoinStatus(String value) {
    	if ("NetSetupUnknownStatus".equals(value)) {
    		return "unknown";
    	} else if ("NetSetupUnjoined".equals(value)) {
    		return "not joined";
    	} else if ("NetSetupWorkgroupName".equals(value)) {
    		return "joined to a workgroup";
    	} else if ("NetSetupDomainName".equals(value)) {
    		return "joined to a domain";
    	}
    	return value;
    }

    @Override
    public boolean supportsCredentialChanges() {
        return false;
    }

    @Override
    public boolean supportsDisplayNameChanges() {
        return false;
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
	public AccountType getAccountType() {
		return AccountType.WINDOWS;
	}

    @Override
    public UserModel authenticate(String username, char[] password) {
		if (isLocalAccount(username)) {
			// local account, bypass Windows authentication
			return super.authenticate(username, password);
		}

		String defaultDomain = settings.getString(Keys.realm.windows.defaultDomain, null);
		if (StringUtils.isEmpty(defaultDomain)) {
			// ensure that default domain is null
			defaultDomain = null;
		}

		if (defaultDomain != null) {
			// sanitize username
			if (username.startsWith(defaultDomain + "\\")) {
				// strip default domain from domain\ username
				username = username.substring(defaultDomain.length() + 1);
			} else if (username.endsWith("@" + defaultDomain)) {
				// strip default domain from username@domain
				username = username.substring(0, username.lastIndexOf('@'));
			}
		}

		IWindowsIdentity identity = null;
		try {
			if (username.indexOf('@') > -1 || username.indexOf('\\') > -1) {
				// manually specified domain
				identity = waffle.logonUser(username, new String(password));
			} else {
				// no domain specified, use default domain
				identity = waffle.logonDomainUser(username, defaultDomain, new String(password));
			}
		} catch (Win32Exception e) {
			logger.error(e.getMessage());
			return null;
		}

		if (identity.isGuest() && !settings.getBoolean(Keys.realm.windows.allowGuests, false)) {
			logger.warn("Guest account access is disabled");
			identity.dispose();
			return null;
		}

        UserModel user = getUserModel(username);
        if (user == null)	// create user object for new authenticated user
        	user = new UserModel(username.toLowerCase());

        // create a user cookie
        if (StringUtils.isEmpty(user.cookie) && !ArrayUtils.isEmpty(password)) {
        	user.cookie = StringUtils.getSHA1(user.username + new String(password));
        }

        // update user attributes from Windows identity
        user.accountType = getAccountType();
        String fqn = identity.getFqn();
        if (fqn.indexOf('\\') > -1) {
        	user.displayName = fqn.substring(fqn.lastIndexOf('\\') + 1);
        } else {
        	user.displayName = fqn;
        }
        user.password = Constants.EXTERNAL_ACCOUNT;

        Set<String> groupNames = new TreeSet<String>();
       	for (IWindowsAccount group : identity.getGroups()) {
       		groupNames.add(group.getFqn());
        }

        if (groupNames.contains("BUILTIN\\Administrators")) {
        	// local administrator
        	user.canAdmin = true;
        }

        // TODO consider mapping Windows groups to teams

        // push the changes to the backing user service
        super.updateUserModel(user);


        // cleanup resources
        identity.dispose();

        return user;
    }
}
