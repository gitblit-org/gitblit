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
import java.net.URI;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.models.TeamModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.StringUtils;
import com.unboundid.ldap.sdk.Attribute;
import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.LDAPSearchException;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchResultEntry;
import com.unboundid.ldap.sdk.SearchScope;
import com.unboundid.util.ssl.SSLUtil;
import com.unboundid.util.ssl.TrustAllTrustManager;

/**
 * Implementation of an LDAP user service.
 * 
 * @author John Crygier
 */
public class LdapUserService extends GitblitUserService {

	public static final Logger logger = LoggerFactory.getLogger(LdapUserService.class);
	
	private IStoredSettings settings;

	public LdapUserService() {
		super();
	}

	@Override
	public void setup(IStoredSettings settings) {
		this.settings = settings;
		String file = settings.getString(Keys.realm.ldap.backingUserService, "users.conf");
		File realmFile = GitBlit.getFileOrFolder(file);

		serviceImpl = createUserService(realmFile);
		logger.info("LDAP User Service backed by " + serviceImpl.toString());
	}
	
	private LDAPConnection getLdapConnection() {
		try {
			URI ldapUrl = new URI(settings.getRequiredString(Keys.realm.ldap.server));
			String bindUserName = settings.getString(Keys.realm.ldap.username, "");
			String bindPassword = settings.getString(Keys.realm.ldap.password, "");
			int ldapPort = ldapUrl.getPort();
			
			if (ldapUrl.getScheme().equalsIgnoreCase("ldaps")) {	// SSL
				if (ldapPort == -1)	// Default Port
					ldapPort = 636;
				
				SSLUtil sslUtil = new SSLUtil(new TrustAllTrustManager()); 
				return new LDAPConnection(sslUtil.createSSLSocketFactory(), ldapUrl.getHost(), ldapPort, bindUserName, bindPassword);
			} else {
				if (ldapPort == -1)	// Default Port
					ldapPort = 389;
				
				return new LDAPConnection(ldapUrl.getHost(), ldapPort, bindUserName, bindPassword);
			}
		} catch (URISyntaxException e) {
			logger.error("Bad LDAP URL, should be in the form: ldap(s)://<server>:<port>", e);
		} catch (GeneralSecurityException e) {
			logger.error("Unable to create SSL Connection", e);
		} catch (LDAPException e) {
			logger.error("Error Connecting to LDAP", e);
		}
		
		return null;
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
		return !settings.getBoolean(Keys.realm.ldap.maintainTeams, false);
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
		String simpleUsername = getSimpleUsername(username);
		
		LDAPConnection ldapConnection = getLdapConnection();		
		if (ldapConnection != null) {
			// Find the logging in user's DN
			String accountBase = settings.getString(Keys.realm.ldap.accountBase, "");
			String accountPattern = settings.getString(Keys.realm.ldap.accountPattern, "(&(objectClass=person)(sAMAccountName=${username}))");
			accountPattern = StringUtils.replace(accountPattern, "${username}", simpleUsername);

			SearchResult result = doSearch(ldapConnection, accountBase, accountPattern);
			if (result != null && result.getEntryCount() == 1) {
				SearchResultEntry loggingInUser = result.getSearchEntries().get(0);
				String loggingInUserDN = loggingInUser.getDN();
				
				if (isAuthenticated(ldapConnection, loggingInUserDN, new String(password))) {
					logger.debug("Authenitcated: " + username);
					
					UserModel user = getUserModel(simpleUsername);
					if (user == null)	// create user object for new authenticated user
						user = createUserFromLdap(simpleUsername, loggingInUser);
					
					user.password = "StoredInLDAP";
					
					if (!supportsTeamMembershipChanges())
						getTeamsFromLdap(ldapConnection, simpleUsername, loggingInUser, user);
					
					// Get Admin Attributes
					setAdminAttribute(user);

					// Push the ldap looked up values to backing file
					super.updateUserModel(user);
					if (!supportsTeamMembershipChanges()) {
						for (TeamModel userTeam : user.teams)
							updateTeamModel(userTeam);
					}
							
					return user;
				}
			}
		}
		
		return null;		
	}

	private void setAdminAttribute(UserModel user) {
	    user.canAdmin = false;
	    List<String>  admins = settings.getStrings(Keys.realm.ldap.admins);
	    for (String admin : admins) {
	        if (admin.startsWith("@")) { // Team
	            if (user.getTeam(admin.substring(1)) != null)
	                user.canAdmin = true;
	        } else
	            if (user.getName().equalsIgnoreCase(admin))
	                user.canAdmin = true;
	    }
	}

	private void getTeamsFromLdap(LDAPConnection ldapConnection, String simpleUsername, SearchResultEntry loggingInUser, UserModel user) {
		String loggingInUserDN = loggingInUser.getDN();
		
		user.teams.clear();		// Clear the users team memberships - we're going to get them from LDAP
		String groupBase = settings.getString(Keys.realm.ldap.groupBase, "");
		String groupMemberPattern = settings.getString(Keys.realm.ldap.groupMemberPattern, "(&(objectClass=group)(member=${dn}))");
		
		groupMemberPattern = StringUtils.replace(groupMemberPattern, "${dn}", loggingInUserDN);
		groupMemberPattern = StringUtils.replace(groupMemberPattern, "${username}", simpleUsername);
		
		// Fill in attributes into groupMemberPattern
		for (Attribute userAttribute : loggingInUser.getAttributes())
			groupMemberPattern = StringUtils.replace(groupMemberPattern, "${" + userAttribute.getName() + "}", userAttribute.getValue());
		
		SearchResult teamMembershipResult = doSearch(ldapConnection, groupBase, groupMemberPattern);
		if (teamMembershipResult != null && teamMembershipResult.getEntryCount() > 0) {
			for (int i = 0; i < teamMembershipResult.getEntryCount(); i++) {
				SearchResultEntry teamEntry = teamMembershipResult.getSearchEntries().get(i);
				String teamName = teamEntry.getAttribute("cn").getValue();
				
				TeamModel teamModel = getTeamModel(teamName);
				if (teamModel == null)
					teamModel = createTeamFromLdap(teamEntry);
					
				user.teams.add(teamModel);
				teamModel.addUser(user.getName());
			}
		}
	}
	
	private TeamModel createTeamFromLdap(SearchResultEntry teamEntry) {
		TeamModel answer = new TeamModel(teamEntry.getAttributeValue("cn"));
		// If attributes other than team name ever from from LDAP, this is where to get them
		
		return answer;		
	}
	
	private UserModel createUserFromLdap(String simpleUserName, SearchResultEntry userEntry) {
		UserModel answer = new UserModel(simpleUserName);
		//If attributes other than user name ever from from LDAP, this is where to get them
		
		return answer;
	}

	private SearchResult doSearch(LDAPConnection ldapConnection, String base, String filter) {
		try {
			return ldapConnection.search(base, SearchScope.SUB, filter);
		} catch (LDAPSearchException e) {
			logger.error("Problem Searching LDAP", e);
			
			return null;
		}
	}
	
	private boolean isAuthenticated(LDAPConnection ldapConnection, String userDn, String password) {
		try {
			ldapConnection.bind(userDn, password);
			return true;
		} catch (LDAPException e) {
			logger.error("Error authenitcating user", e);
			return false;
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
}
