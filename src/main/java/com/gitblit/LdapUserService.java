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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.Constants.AccountType;
import com.gitblit.models.TeamModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.StringUtils;
import com.unboundid.ldap.sdk.Attribute;
import com.unboundid.ldap.sdk.ExtendedResult;
import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.LDAPSearchException;
import com.unboundid.ldap.sdk.ResultCode;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchResultEntry;
import com.unboundid.ldap.sdk.SearchScope;
import com.unboundid.ldap.sdk.extensions.StartTLSExtendedRequest;
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
    private AtomicLong lastLdapUserSync = new AtomicLong(0L);
	
	public LdapUserService() {
		super();
	}

 	private long getSynchronizationPeriod() {
        final String cacheDuration = settings.getString(Keys.realm.ldap.ldapCachePeriod, "2 MINUTES");
        try {
            final String[] s = cacheDuration.split(" ", 2);
            long duration = Long.parseLong(s[0]);
            TimeUnit timeUnit = TimeUnit.valueOf(s[1]);
            return timeUnit.toMillis(duration);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException(Keys.realm.ldap.ldapCachePeriod + " must have format '<long> <TimeUnit>' where <TimeUnit> is one of 'MILLISECONDS', 'SECONDS', 'MINUTES', 'HOURS', 'DAYS'");
        }
    }
    
	@Override
	public void setup(IStoredSettings settings) {
		this.settings = settings;
		String file = settings.getString(Keys.realm.ldap.backingUserService, "${baseFolder}/users.conf");
		File realmFile = GitBlit.getFileOrFolder(file);
		
		serviceImpl = createUserService(realmFile);
		logger.info("LDAP User Service backed by " + serviceImpl.toString());
		
		synchronizeLdapUsers();
	}
	
	protected synchronized void synchronizeLdapUsers() {
        final boolean enabled = settings.getBoolean(Keys.realm.ldap.synchronizeUsers.enable, false);
        if (enabled) {
            if (System.currentTimeMillis() > (lastLdapUserSync.get() + getSynchronizationPeriod())) {
            	logger.info("Synchronizing with LDAP @ " + settings.getRequiredString(Keys.realm.ldap.server));
                final boolean deleteRemovedLdapUsers = settings.getBoolean(Keys.realm.ldap.synchronizeUsers.removeDeleted, true);
                LDAPConnection ldapConnection = getLdapConnection();
                if (ldapConnection != null) {
                    try {
                        String accountBase = settings.getString(Keys.realm.ldap.accountBase, "");
                        String uidAttribute = settings.getString(Keys.realm.ldap.uid, "uid");
                        String accountPattern = settings.getString(Keys.realm.ldap.accountPattern, "(&(objectClass=person)(sAMAccountName=${username}))");
                        accountPattern = StringUtils.replace(accountPattern, "${username}", "*");

                        SearchResult result = doSearch(ldapConnection, accountBase, accountPattern);
                        if (result != null && result.getEntryCount() > 0) {
                            final Map<String, UserModel> ldapUsers = new HashMap<String, UserModel>();

                            for (SearchResultEntry loggingInUser : result.getSearchEntries()) {

                                final String username = loggingInUser.getAttribute(uidAttribute).getValue();
                                logger.debug("LDAP synchronizing: " + username);

                                UserModel user = getUserModel(username);
                                if (user == null) {
                                    user = new UserModel(username);
                                }

                                if (!supportsTeamMembershipChanges())
                                    getTeamsFromLdap(ldapConnection, username, loggingInUser, user);

                                // Get User Attributes
                                setUserAttributes(user, loggingInUser);

                                // store in map
                                ldapUsers.put(username.toLowerCase(), user);
                            }

                            if (deleteRemovedLdapUsers) {
                                logger.debug("detecting removed LDAP users...");

                                for (UserModel userModel : super.getAllUsers()) {
                                    if (Constants.EXTERNAL_ACCOUNT.equals(userModel.password)) {
                                        if (! ldapUsers.containsKey(userModel.username)) {
                                            logger.info("deleting removed LDAP user " + userModel.username + " from backing user service");
                                            super.deleteUser(userModel.username);
                                        }
                                    }
                                }
                            }

                            super.updateUserModels(ldapUsers.values());

                            if (!supportsTeamMembershipChanges()) {
                                final Map<String, TeamModel> userTeams = new HashMap<String, TeamModel>();
                                for (UserModel user : ldapUsers.values()) {
                                    for (TeamModel userTeam : user.teams) {
                                        userTeams.put(userTeam.name, userTeam);
                                    }
                                }
                                updateTeamModels(userTeams.values());
                            }
                        }
                        lastLdapUserSync.set(System.currentTimeMillis()); 
                    } finally {
                        ldapConnection.close();
                    }
                }
            }
        }
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
				
				LDAPConnection conn = new LDAPConnection(ldapUrl.getHost(), ldapPort, bindUserName, bindPassword);

				if (ldapUrl.getScheme().equalsIgnoreCase("ldap+tls")) {
					SSLUtil sslUtil = new SSLUtil(new TrustAllTrustManager());

					ExtendedResult extendedResult = conn.processExtendedOperation(
						new StartTLSExtendedRequest(sslUtil.createSSLContext()));

					if (extendedResult.getResultCode() != ResultCode.SUCCESS) {
						throw new LDAPException(extendedResult.getResultCode());
					}
				}
				return conn;
			}
		} catch (URISyntaxException e) {
			logger.error("Bad LDAP URL, should be in the form: ldap(s|+tls)://<server>:<port>", e);
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
	 * If no displayName pattern is defined then Gitblit can manage the display name.
	 *
	 * @return true if Gitblit can manage the user display name
	 * @since 1.0.0
	 */
	@Override
	public boolean supportsDisplayNameChanges() {
		return StringUtils.isEmpty(settings.getString(Keys.realm.ldap.displayName, ""));
	}
	
	/**
	 * If no email pattern is defined then Gitblit can manage the email address.
	 *
	 * @return true if Gitblit can manage the user email address
	 * @since 1.0.0
	 */
	@Override
	public boolean supportsEmailAddressChanges() {
		return StringUtils.isEmpty(settings.getString(Keys.realm.ldap.email, ""));
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
	
	@Override
	protected AccountType getAccountType() {
		 return AccountType.LDAP;
	}

	@Override
	public UserModel authenticate(String username, char[] password) {
		if (isLocalAccount(username)) {
			// local account, bypass LDAP authentication
			return super.authenticate(username, password);
		}
		
		String simpleUsername = getSimpleUsername(username);
		
		LDAPConnection ldapConnection = getLdapConnection();
		if (ldapConnection != null) {
			try {
				// Find the logging in user's DN
				String accountBase = settings.getString(Keys.realm.ldap.accountBase, "");
				String accountPattern = settings.getString(Keys.realm.ldap.accountPattern, "(&(objectClass=person)(sAMAccountName=${username}))");
				accountPattern = StringUtils.replace(accountPattern, "${username}", escapeLDAPSearchFilter(simpleUsername));

				SearchResult result = doSearch(ldapConnection, accountBase, accountPattern);
				if (result != null && result.getEntryCount() == 1) {
					SearchResultEntry loggingInUser = result.getSearchEntries().get(0);
					String loggingInUserDN = loggingInUser.getDN();

					if (isAuthenticated(ldapConnection, loggingInUserDN, new String(password))) {
						logger.debug("LDAP authenticated: " + username);

						UserModel user = null;
						synchronized (this) {
							user = getUserModel(simpleUsername);
							if (user == null)	// create user object for new authenticated user
								user = new UserModel(simpleUsername);

							// create a user cookie
							if (StringUtils.isEmpty(user.cookie) && !ArrayUtils.isEmpty(password)) {
								user.cookie = StringUtils.getSHA1(user.username + new String(password));
							}

							if (!supportsTeamMembershipChanges())
								getTeamsFromLdap(ldapConnection, simpleUsername, loggingInUser, user);

							// Get User Attributes
							setUserAttributes(user, loggingInUser);

							// Push the ldap looked up values to backing file
							super.updateUserModel(user);
							if (!supportsTeamMembershipChanges()) {
								for (TeamModel userTeam : user.teams)
									updateTeamModel(userTeam);
							}
						}
						
						return user;
					}
				}
			} finally {
				ldapConnection.close();
			}
		}
		return null;		
	}

	/**
	 * Set the admin attribute from team memberships retrieved from LDAP.
	 * If we are not storing teams in LDAP and/or we have not defined any
	 * administrator teams, then do not change the admin flag.
	 * 
	 * @param user
	 */
	private void setAdminAttribute(UserModel user) {
		if (!supportsTeamMembershipChanges()) {
			List<String> admins = settings.getStrings(Keys.realm.ldap.admins);
			// if we have defined administrative teams, then set admin flag
			// otherwise leave admin flag unchanged
			if (!ArrayUtils.isEmpty(admins)) {
				user.canAdmin = false;
				for (String admin : admins) {
					if (admin.startsWith("@")) { // Team
						if (user.getTeam(admin.substring(1)) != null)
							user.canAdmin = true;
					} else
						if (user.getName().equalsIgnoreCase(admin))
							user.canAdmin = true;
				}
			}
		}
	}
	
	private void setUserAttributes(UserModel user, SearchResultEntry userEntry) {
		// Is this user an admin?
		setAdminAttribute(user);
		
		// Don't want visibility into the real password, make up a dummy
		user.password = Constants.EXTERNAL_ACCOUNT;
		user.accountType = getAccountType();
		
		// Get full name Attribute
		String displayName = settings.getString(Keys.realm.ldap.displayName, "");		
		if (!StringUtils.isEmpty(displayName)) {
			// Replace embedded ${} with attributes
			if (displayName.contains("${")) {
				for (Attribute userAttribute : userEntry.getAttributes())
					displayName = StringUtils.replace(displayName, "${" + userAttribute.getName() + "}", userAttribute.getValue());

				user.displayName = displayName;
			} else {
				Attribute attribute = userEntry.getAttribute(displayName);
				if (attribute != null && attribute.hasValue()) {
					user.displayName = attribute.getValue();
				}
			}
		}
		
		// Get email address Attribute
		String email = settings.getString(Keys.realm.ldap.email, "");
		if (!StringUtils.isEmpty(email)) {
			if (email.contains("${")) {
				for (Attribute userAttribute : userEntry.getAttributes())
					email = StringUtils.replace(email, "${" + userAttribute.getName() + "}", userAttribute.getValue());

				user.emailAddress = email;
			} else {
				Attribute attribute = userEntry.getAttribute(email);
				if (attribute != null && attribute.hasValue()) {
					user.emailAddress = attribute.getValue();
				}
			}
		}
	}

	private void getTeamsFromLdap(LDAPConnection ldapConnection, String simpleUsername, SearchResultEntry loggingInUser, UserModel user) {
		String loggingInUserDN = loggingInUser.getDN();
		
		user.teams.clear();		// Clear the users team memberships - we're going to get them from LDAP
		String groupBase = settings.getString(Keys.realm.ldap.groupBase, "");
		String groupMemberPattern = settings.getString(Keys.realm.ldap.groupMemberPattern, "(&(objectClass=group)(member=${dn}))");
		
		groupMemberPattern = StringUtils.replace(groupMemberPattern, "${dn}", escapeLDAPSearchFilter(loggingInUserDN));
		groupMemberPattern = StringUtils.replace(groupMemberPattern, "${username}", escapeLDAPSearchFilter(simpleUsername));
		
		// Fill in attributes into groupMemberPattern
		for (Attribute userAttribute : loggingInUser.getAttributes())
			groupMemberPattern = StringUtils.replace(groupMemberPattern, "${" + userAttribute.getName() + "}", escapeLDAPSearchFilter(userAttribute.getValue()));
		
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
		// potentially retrieve other attributes here in the future
		
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
			// Binding will stop any LDAP-Injection Attacks since the searched-for user needs to bind to that DN
			ldapConnection.bind(userDn, password);
			return true;
		} catch (LDAPException e) {
			logger.error("Error authenticating user", e);
			return false;
		}
	}

    @Override
    public List<String> getAllUsernames() {
        synchronizeLdapUsers();
        return super.getAllUsernames();
    }

    @Override
    public List<UserModel> getAllUsers() {
        synchronizeLdapUsers();
        return super.getAllUsers();
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
	
	// From: https://www.owasp.org/index.php/Preventing_LDAP_Injection_in_Java
	public static final String escapeLDAPSearchFilter(String filter) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < filter.length(); i++) {
			char curChar = filter.charAt(i);
			switch (curChar) {
			case '\\':
				sb.append("\\5c");
				break;
			case '*':
				sb.append("\\2a");
				break;
			case '(':
				sb.append("\\28");
				break;
			case ')':
				sb.append("\\29");
				break;
			case '\u0000': 
				sb.append("\\00"); 
				break;
			default:
				sb.append(curChar);
			}
		}
		return sb.toString();
	}
}
