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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.Constants.AccountType;
import com.gitblit.Constants.Role;
import com.gitblit.IStoredSettings;
import com.gitblit.manager.IRuntimeManager;
import com.gitblit.manager.IUserManager;
import com.gitblit.models.TeamModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.StringUtils;

public abstract class AuthenticationProvider {

	public static NullProvider NULL_PROVIDER = new NullProvider();

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected final String serviceName;

	protected File baseFolder;

	protected IStoredSettings settings;

	protected IRuntimeManager runtimeManager;

	protected IUserManager userManager;

	protected AuthenticationProvider(String serviceName) {
		this.serviceName = serviceName;
	}

	/**
	 * Returns the file object for the specified configuration key.
	 *
	 * @return the file
	 */
	public File getFileOrFolder(String key, String defaultFileOrFolder) {
		return runtimeManager.getFileOrFolder(key, defaultFileOrFolder);
	}

	public final void setup(IRuntimeManager runtimeManager, IUserManager userManager) {
		this.baseFolder = runtimeManager.getBaseFolder();
		this.settings = runtimeManager.getSettings();
		this.runtimeManager = runtimeManager;
		this.userManager = userManager;
		setup();
	}

	public String getServiceName() {
		return serviceName;
	}

	protected void setCookie(UserModel user, char [] password) {
		// create a user cookie
		if (StringUtils.isEmpty(user.cookie) && !ArrayUtils.isEmpty(password)) {
			user.cookie = StringUtils.getSHA1(user.username + new String(password));
		}
	}

	protected void updateUser(UserModel userModel) {
		// TODO implement user model change detection
		// account for new user and revised user

		// username
		// displayname
		// email address
		// cookie

		userManager.updateUserModel(userModel);
	}

	protected void updateTeam(TeamModel teamModel) {
		// TODO implement team model change detection
		// account for new team and revised team

		// memberships

		userManager.updateTeamModel(teamModel);
	}

	public abstract void setup();

	public abstract void stop();

	public abstract UserModel authenticate(String username, char[] password);

	public abstract AccountType getAccountType();

	/**
	 * Does the user service support changes to credentials?
	 *
	 * @return true or false
	 * @since 1.0.0
	 */
	public abstract boolean supportsCredentialChanges();

	/**
	 * Returns true if the user's display name can be changed.
	 *
	 * @param user
	 * @return true if the user service supports display name changes
	 */
	public abstract boolean supportsDisplayNameChanges();

	/**
	 * Returns true if the user's email address can be changed.
	 *
	 * @param user
	 * @return true if the user service supports email address changes
	 */
	public abstract boolean supportsEmailAddressChanges();

	/**
	 * Returns true if the user's team memberships can be changed.
	 *
	 * @param user
	 * @return true if the user service supports team membership changes
	 */
	public abstract boolean supportsTeamMembershipChanges();

	/**
	 * Returns true if the user's role can be changed.
	 *
	 * @param user
	 * @param role
	 * @return true if the user's role can be changed
	 */
	public abstract boolean supportsRoleChanges(UserModel user, Role role);

	/**
	 * Returns true if the team's role can be changed.
	 *
	 * @param user
	 * @param role
	 * @return true if the team's role can be changed
	 */
	public abstract boolean supportsRoleChanges(TeamModel team, Role role);

    @Override
    public String toString() {
    	return getServiceName() + " (" + getClass().getName() + ")";
    }

    public abstract static class UsernamePasswordAuthenticationProvider extends AuthenticationProvider {
    	protected UsernamePasswordAuthenticationProvider(String serviceName) {
    		super(serviceName);
    	}

    	@Override
		public void stop() {

		}
    }

    public static class NullProvider extends AuthenticationProvider {

		protected NullProvider() {
			super("NULL");
		}

		@Override
		public void setup() {

		}

		@Override
		public void stop() {

		}

		@Override
		public UserModel authenticate(String username, char[] password) {
			return null;
		}

		@Override
		public AccountType getAccountType() {
			return AccountType.LOCAL;
		}

		@Override
		public boolean supportsCredentialChanges() {
			return true;
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

    }
}
