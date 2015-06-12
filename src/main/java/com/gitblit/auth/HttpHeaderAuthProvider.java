/*
 * Copyright 2015 gitblit.com.
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

import java.util.HashSet;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.Constants;
import com.gitblit.Constants.AccountType;
import com.gitblit.Constants.AuthenticationType;
import com.gitblit.Constants.Role;
import com.gitblit.Keys;
import com.gitblit.models.TeamModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.StringUtils;

public class HttpHeaderAuthProvider extends AuthenticationProvider {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected String userHeaderName;
    protected String teamHeaderName;
    protected String teamHeaderSeparator;

	public HttpHeaderAuthProvider() {
		super("httpheader");
	}

	@Override
	public void setup() {
		// Load HTTP header configuration
		userHeaderName = settings.getString(Keys.realm.httpheader.userheader, null);
		teamHeaderName = settings.getString(Keys.realm.httpheader.teamheader, null);
		teamHeaderSeparator = settings.getString(Keys.realm.httpheader.teamseparator, ",");

		if (StringUtils.isEmpty(userHeaderName)) {
			logger.warn("HTTP Header authentication is enabled, but no header is not defined in " + Keys.realm.httpheader.userheader);
		}
	}

	@Override
	public void stop() {}


	@Override
	public UserModel authenticate(HttpServletRequest httpRequest) {
		// Try to authenticate using custom HTTP header if user header is defined
		if (!StringUtils.isEmpty(userHeaderName)) {
			String headerUserName = httpRequest.getHeader(userHeaderName);
			if (!StringUtils.isEmpty(headerUserName) && !userManager.isInternalAccount(headerUserName)) {
				// We have a user, try to load team names as well
				Set<TeamModel> userTeams = new HashSet<>();
				if (!StringUtils.isEmpty(teamHeaderName)) {
					String headerTeamValue = httpRequest.getHeader(teamHeaderName);
					if (!StringUtils.isEmpty(headerTeamValue)) {
						String[] headerTeamNames = headerTeamValue.split(teamHeaderSeparator);
						for (String teamName : headerTeamNames) {
							teamName = teamName.trim();
							if (!StringUtils.isEmpty(teamName)) {
								TeamModel team = userManager.getTeamModel(teamName);
								if (null == team) {
									// Create teams here so they can marked with the correct AccountType
									team = new TeamModel(teamName);
									team.accountType = AccountType.HTTPHEADER;
									updateTeam(team);
								}
								userTeams.add(team);
							}
						}
					}
				}

				UserModel user = userManager.getUserModel(headerUserName);
				if (user != null) {
					// If team header is provided in request, reset all team memberships, even if resetting to empty set
					if (!StringUtils.isEmpty(teamHeaderName)) {
						user.teams.clear();
						user.teams.addAll(userTeams);
					}
					updateUser(user);
					return user;
				} else if (settings.getBoolean(Keys.realm.httpheader.autoCreateAccounts, false)) {
					// auto-create user from HTTP header
					user = new UserModel(headerUserName.toLowerCase());
					user.displayName = headerUserName;
					user.password = Constants.EXTERNAL_ACCOUNT;
					user.accountType = AccountType.HTTPHEADER;
					user.teams.addAll(userTeams);
					updateUser(user);
					return user;
				}
			}
		}

		return null;
	}

	@Override
	public UserModel authenticate(String username, char[] password){
		// Username/password is not supported for HTTP header authentication
		return null;
	}

	@Override
	public AccountType getAccountType() {
		return AccountType.HTTPHEADER;
	}

	@Override
	public AuthenticationType getAuthenticationType() {
		return AuthenticationType.HTTPHEADER;
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
		return false;
	}

	@Override
	public boolean supportsTeamMembershipChanges() {
		return StringUtils.isEmpty(teamHeaderName);
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