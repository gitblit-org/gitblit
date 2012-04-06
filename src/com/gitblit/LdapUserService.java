/*
 * Copyright 2012 John Crygier
 * Copyright 2012 gitblit.com
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
import java.text.MessageFormat;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.models.TeamModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.ConnectionUtils.BlindSSLSocketFactory;
import com.gitblit.utils.StringUtils;

/**
 * Implementation of an LDAP user service.
 * 
 * @author John Crygier
 */
public class LdapUserService extends GitblitUserService {

	public static final Logger logger = LoggerFactory.getLogger(LdapUserService.class);
	private final String CONTEXT_FACTORY = "com.sun.jndi.ldap.LdapCtxFactory";

	private IStoredSettings settings;

	public LdapUserService() {
		super();
	}

	@Override
	public void setup(IStoredSettings settings) {
		this.settings = settings;
		String file = settings.getString(Keys.realm.ldap_backingUserService, "users.conf");
		File realmFile = GitBlit.getFileOrFolder(file);

		serviceImpl = createUserService(realmFile);
		logger.info("LDAP User Service backed by " + serviceImpl.toString());
	}
	
	/**
	 * Credentials are defined in the LDAP server and can not be manipulated
	 * from Gitblit.
	 *
	 * @return false
	 * @since 1.0.0
	 */
	@Override
	public boolean supportsCredentialChanges() {
		return false;
	}
	
	/**
	 * If the LDAP server will maintain team memberships then LdapUserService
	 * will not allow team membership changes.  In this scenario all team
	 * changes must be made on the LDAP server by the LDAP administrator.
	 * 
	 * @return true or false
	 * @since 1.0.0
	 */	
	public boolean supportsTeamMembershipChanges() {
		return !settings.getBoolean(Keys.realm.ldap_maintainTeams, false);
	}

	/**
	 * Does the user service support cookie authentication?
	 * 
	 * @return true or false
	 */
	@Override
	public boolean supportsCookies() {
		// TODO cookies need to be reviewed
		return false;
	}

	@Override
	public UserModel authenticate(String username, char[] password) {
		String domainUser = getDomainUsername(username);		
		DirContext ctx = getDirContext(domainUser, new String(password));
		// TODO do we need a bind here?
		if (ctx != null) {
			String simpleUsername = getSimpleUsername(username);
			UserModel user = getUserModel(simpleUsername);
			if (user == null) {
				// create user object for new authenticated user
				user = new UserModel(simpleUsername.toLowerCase());
			}
			user.password = new String(password);

			if (!supportsTeamMembershipChanges()) {
				// Teams are specified in LDAP server
				// TODO search LDAP for team memberships
				Set<String> foundTeams = new HashSet<String>();
				for (String team : foundTeams) {					
					TeamModel model = getTeamModel(team);
					if (model == null) {
						// create the team
						model = new TeamModel(team.toLowerCase());
						updateTeamModel(model);
					}
					// add team to the user
					user.teams.add(model);
				}
			}
			
			try {
				ctx.close();
			} catch (NamingException e) {
				logger.error("Can not close context", e);
			}
			return user;
		}		
		return null;		
	}	
	
	protected DirContext getDirContext() {
		String username = settings.getString(Keys.realm.ldap_username, "");
		String password = settings.getString(Keys.realm.ldap_password, "");
		return getDirContext(username, password);
	}

	protected DirContext getDirContext(String username, String password) {
		try {
			String server = settings.getRequiredString(Keys.realm.ldap_server);
			Hashtable<String, String> env = new Hashtable<String, String>();
			env.put(Context.INITIAL_CONTEXT_FACTORY, CONTEXT_FACTORY);
			env.put(Context.PROVIDER_URL, server);
			if (server.startsWith("ldaps:")) {
				env.put("java.naming.ldap.factory.socket", BlindSSLSocketFactory.class.getName());
			}
			// TODO consider making this a setting
			env.put("com.sun.jndi.ldap.read.timeout", "5000");

			if (!StringUtils.isEmpty(username)) {
				// authenticated login
				env.put(Context.SECURITY_AUTHENTICATION, "simple");
				env.put(Context.SECURITY_PRINCIPAL, getDomainUsername(username));
				env.put(Context.SECURITY_CREDENTIALS, password == null ? "":password.trim());
			}
			return new InitialDirContext(env);
		} catch (NamingException e) {
			logger.warn(MessageFormat.format("Error connecting to LDAP with credentials. Please check {0}, {1}, and {2}",
					Keys.realm.ldap_server, Keys.realm.ldap_username, Keys.realm.ldap_password), e);
			return null;
		}
	}
	
	/**
	 * Returns a simple username without any domain prefixes.
	 * 
	 * @param username
	 * @return a simple username
	 */
	protected String getSimpleUsername(String username) {
		int lastSlash = username.lastIndexOf('\\');
		if (lastSlash > -1) {
			username = username.substring(lastSlash + 1);
		}
		return username;
	}

	/**
	 * Returns a username with a domain prefix as long as the username does not
	 * already have a comain prefix.
	 * 
	 * @param username
	 * @return a domain username
	 */
	protected String getDomainUsername(String username) {
		String domain = settings.getString(Keys.realm.ldap_domain, null);
		String domainUsername = username;
		if (!StringUtils.isEmpty(domain) && (domainUsername.indexOf('\\') == -1)) {
			domainUsername = domain + "\\" + username;
		}
		return domainUsername.trim();
	}
}
