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
package com.gitblit.auth;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.gitblit.Constants;
import com.gitblit.Constants.AccountType;
import com.gitblit.Constants.Role;
import com.gitblit.Keys;
import com.gitblit.auth.AuthenticationProvider.UsernamePasswordAuthenticationProvider;
import com.gitblit.models.TeamModel;
import com.gitblit.models.UserModel;
import com.gitblit.service.LdapSyncService;
import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.StringUtils;
import com.unboundid.ldap.sdk.Attribute;
import com.unboundid.ldap.sdk.BindRequest;
import com.unboundid.ldap.sdk.BindResult;
import com.unboundid.ldap.sdk.DereferencePolicy;
import com.unboundid.ldap.sdk.ExtendedResult;
import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.LDAPSearchException;
import com.unboundid.ldap.sdk.ResultCode;
import com.unboundid.ldap.sdk.SearchRequest;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchResultEntry;
import com.unboundid.ldap.sdk.SearchScope;
import com.unboundid.ldap.sdk.SimpleBindRequest;
import com.unboundid.ldap.sdk.extensions.StartTLSExtendedRequest;
import com.unboundid.util.ssl.SSLUtil;
import com.unboundid.util.ssl.TrustAllTrustManager;

/**
 * Implementation of an LDAP user service.
 *
 * @author John Crygier
 */
public class LdapAuthProvider extends UsernamePasswordAuthenticationProvider {

	private final ScheduledExecutorService scheduledExecutorService;

	public LdapAuthProvider() {
		super("ldap");

		scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
	}

 	private long getSynchronizationPeriodInMilliseconds() {
 		String period = settings.getString(Keys.realm.ldap.syncPeriod, null);
 		if (StringUtils.isEmpty(period)) {
 	 		period = settings.getString("realm.ldap.ldapCachePeriod", null);
 	 		if (StringUtils.isEmpty(period)) {
 	 			period = "5 MINUTES";
 	 		} else {
 	 			logger.warn("realm.ldap.ldapCachePeriod is obsolete!");
 	 			logger.warn(MessageFormat.format("Please set {0}={1} in gitblit.properties!", Keys.realm.ldap.syncPeriod, period));
 	 			settings.overrideSetting(Keys.realm.ldap.syncPeriod, period);
 	 		}
 		}

        try {
            final String[] s = period.split(" ", 2);
            long duration = Math.abs(Long.parseLong(s[0]));
            TimeUnit timeUnit = TimeUnit.valueOf(s[1]);
            return timeUnit.toMillis(duration);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException(Keys.realm.ldap.syncPeriod + " must have format '<long> <TimeUnit>' where <TimeUnit> is one of 'MILLISECONDS', 'SECONDS', 'MINUTES', 'HOURS', 'DAYS'");
        }
    }

	@Override
	public void setup() {
		configureSyncService();
	}

	@Override
	public void stop() {
		scheduledExecutorService.shutdownNow();
	}

	public synchronized void sync() {
		final boolean enabled = settings.getBoolean(Keys.realm.ldap.synchronize, false);
		if (enabled) {
			logger.info("Synchronizing with LDAP @ " + settings.getRequiredString(Keys.realm.ldap.server));
			final boolean deleteRemovedLdapUsers = settings.getBoolean(Keys.realm.ldap.removeDeletedUsers, true);
			LdapConnection ldapConnection = new LdapConnection();
			if (ldapConnection.connect()) {
				if (ldapConnection.bind() == null) {
					ldapConnection.close();
					logger.error("Cannot synchronize with LDAP.");
					return;
				}

				try {
					String accountBase = settings.getString(Keys.realm.ldap.accountBase, "");
					String uidAttribute = settings.getString(Keys.realm.ldap.uid, "uid");
					String accountPattern = settings.getString(Keys.realm.ldap.accountPattern, "(&(objectClass=person)(sAMAccountName=${username}))");
					accountPattern = StringUtils.replace(accountPattern, "${username}", "*");

					SearchResult result = doSearch(ldapConnection, accountBase, accountPattern);
					if (result != null && result.getEntryCount() > 0) {
						final Map<String, UserModel> ldapUsers = new HashMap<String, UserModel>();

						for (SearchResultEntry loggingInUser : result.getSearchEntries()) {
							Attribute uid = loggingInUser.getAttribute(uidAttribute);
							if (uid == null) {
								logger.error("Can not synchronize with LDAP, missing \"{}\" attribute", uidAttribute);
								continue;
							}
							final String username = uid.getValue();
							logger.debug("LDAP synchronizing: " + username);

							UserModel user = userManager.getUserModel(username);
							if (user == null) {
								user = new UserModel(username);
							}

							if (!supportsTeamMembershipChanges()) {
								getTeamsFromLdap(ldapConnection, username, loggingInUser, user);
							}

							// Get User Attributes
							setUserAttributes(user, loggingInUser);

							// store in map
							ldapUsers.put(username.toLowerCase(), user);
						}

						if (deleteRemovedLdapUsers) {
							logger.debug("detecting removed LDAP users...");

							for (UserModel userModel : userManager.getAllUsers()) {
								if (AccountType.LDAP == userModel.accountType) {
									if (!ldapUsers.containsKey(userModel.username)) {
										logger.info("deleting removed LDAP user " + userModel.username + " from user service");
										userManager.deleteUser(userModel.username);
									}
								}
							}
						}

						userManager.updateUserModels(ldapUsers.values());

						if (!supportsTeamMembershipChanges()) {
							final Map<String, TeamModel> userTeams = new HashMap<String, TeamModel>();
							for (UserModel user : ldapUsers.values()) {
								for (TeamModel userTeam : user.teams) {
									// Is this an administrative team?
									setAdminAttribute(userTeam);
									userTeams.put(userTeam.name, userTeam);
								}
							}
							userManager.updateTeamModels(userTeams.values());
						}
					}
					if (!supportsTeamMembershipChanges()) {
						getEmptyTeamsFromLdap(ldapConnection);
					}
				} finally {
					ldapConnection.close();
				}
			}
		}
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
	@Override
	public boolean supportsTeamMembershipChanges() {
		return !settings.getBoolean(Keys.realm.ldap.maintainTeams, false);
	}

    @Override
    public boolean supportsRoleChanges(UserModel user, Role role) {
    	if (Role.ADMIN == role) {
    		if (!supportsTeamMembershipChanges()) {
				return false;
    		}
    	}
        return true;
    }

	@Override
	public boolean supportsRoleChanges(TeamModel team, Role role) {
		if (Role.ADMIN == role) {
    		if (!supportsTeamMembershipChanges()) {
				return false;
    		}
    	}
		return true;
	}

	@Override
	public AccountType getAccountType() {
		 return AccountType.LDAP;
	}

	@Override
	public UserModel authenticate(String username, char[] password) {
		String simpleUsername = getSimpleUsername(username);

		LdapConnection ldapConnection = new LdapConnection();
		if (ldapConnection.connect()) {

			// Try to bind either to the "manager" account,
			// or directly to the DN of the user logging in, if realm.ldap.bindpattern is configured.
			String passwd = new String(password);
			BindResult bindResult = null;
			String bindPattern = settings.getString(Keys.realm.ldap.bindpattern, "");
			if (! StringUtils.isEmpty(bindPattern)) {
				bindResult = ldapConnection.bind(bindPattern, simpleUsername, passwd);
			} else {
				bindResult = ldapConnection.bind();
			}
			if (bindResult == null) {
				ldapConnection.close();
				return null;
			}


			try {
				// Find the logging in user's DN
				String accountBase = settings.getString(Keys.realm.ldap.accountBase, "");
				String accountPattern = settings.getString(Keys.realm.ldap.accountPattern, "(&(objectClass=person)(sAMAccountName=${username}))");
				accountPattern = StringUtils.replace(accountPattern, "${username}", escapeLDAPSearchFilter(simpleUsername));

				SearchResult result = doSearch(ldapConnection, accountBase, accountPattern);
				if (result != null && result.getEntryCount() == 1) {
					SearchResultEntry loggingInUser = result.getSearchEntries().get(0);
					String loggingInUserDN = loggingInUser.getDN();

					if (ldapConnection.isAuthenticated(loggingInUserDN, passwd)) {
						logger.debug("LDAP authenticated: " + username);

						UserModel user = null;
						synchronized (this) {
							user = userManager.getUserModel(simpleUsername);
							if (user == null) {
								// create user object for new authenticated user
								user = new UserModel(simpleUsername);
							}

							// create a user cookie
							setCookie(user);

							if (!supportsTeamMembershipChanges()) {
								getTeamsFromLdap(ldapConnection, simpleUsername, loggingInUser, user);
							}

							// Get User Attributes
							setUserAttributes(user, loggingInUser);

							// Push the ldap looked up values to backing file
							updateUser(user);

							if (!supportsTeamMembershipChanges()) {
								for (TeamModel userTeam : user.teams) {
									// Is this an administrative team?
									setAdminAttribute(userTeam);
									updateTeam(userTeam);
								}
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
					if (user.getName().equalsIgnoreCase(admin)) {
						// admin user
						user.canAdmin = true;
					}
				}
			}
		}
	}

	/**
	 * Set the canAdmin attribute for team retrieved from LDAP.
	 * If we are not storing teams in LDAP and/or we have not defined any
	 * administrator teams, then do not change the admin flag.
	 *
	 * @param team
	 */
	private void setAdminAttribute(TeamModel team) {
		if (!supportsTeamMembershipChanges()) {
			List<String> admins = settings.getStrings(Keys.realm.ldap.admins);
			// if we have defined administrative teams, then set admin flag
			// otherwise leave admin flag unchanged
			if (!ArrayUtils.isEmpty(admins)) {
				team.canAdmin = false;
				for (String admin : admins) {
					if (admin.startsWith("@") && team.name.equalsIgnoreCase(admin.substring(1))) {
						// admin team
						team.canAdmin = true;
					}
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
				for (Attribute userAttribute : userEntry.getAttributes()) {
					displayName = StringUtils.replace(displayName, "${" + userAttribute.getName() + "}", userAttribute.getValue());
				}
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
				for (Attribute userAttribute : userEntry.getAttributes()) {
					email = StringUtils.replace(email, "${" + userAttribute.getName() + "}", userAttribute.getValue());
				}
				user.emailAddress = email;
			} else {
				Attribute attribute = userEntry.getAttribute(email);
				if (attribute != null && attribute.hasValue()) {
					user.emailAddress = attribute.getValue();
				} else {
					// issue-456/ticket-134
					// allow LDAP to delete an email address
					user.emailAddress = null;
				}
			}
		}
	}

    private void getTeamsFromLdap(LdapConnection ldapConnection, String simpleUsername, SearchResultEntry loggingInUser, UserModel user) {
        logger.info("LDAP getTeamsFromLdap: " + simpleUsername);
        boolean isAdmin = false;
        List<String> admins = settings.getStrings(Keys.realm.ldap.admins);
        String loggingInUserDN = loggingInUser.getDN();

        // Clear the users team memberships - we're going to get them from LDAP
        user.teams.clear();

        String groupBase = settings.getString(Keys.realm.ldap.groupBase, "");
        String groupMemberPattern = settings.getString(Keys.realm.ldap.groupMemberPattern, "(&(objectClass=group)(member=${dn}))");

        groupMemberPattern = StringUtils.replace(groupMemberPattern, "${dn}", escapeLDAPSearchFilter(loggingInUserDN));
        groupMemberPattern = StringUtils.replace(groupMemberPattern, "${username}", escapeLDAPSearchFilter(simpleUsername));

        // Fill in attributes into groupMemberPattern
        for (Attribute userAttribute : loggingInUser.getAttributes()) {
            groupMemberPattern = StringUtils.replace(groupMemberPattern, "${" + userAttribute.getName() + "}", escapeLDAPSearchFilter(userAttribute.getValue()));
        }

        SearchResult teamMembershipResult = searchTeamsInLdap(ldapConnection, groupBase, true, groupMemberPattern, Arrays.asList("cn"));
        if (teamMembershipResult != null && teamMembershipResult.getEntryCount() > 0) {
            for (int i = 0; i < teamMembershipResult.getEntryCount(); i++) {
                SearchResultEntry teamEntry = teamMembershipResult.getSearchEntries().get(i);
                String teamName = teamEntry.getAttribute("cn").getValue();
                logger.info("LDAP Team: " + teamName);
                TeamModel teamModel = userManager.getTeamModel(teamName);
                if (teamModel == null) {
                    teamModel = createTeamFromLdap(teamEntry);
                }

                user.teams.add(teamModel);
                teamModel.addUser(user.getName());

			// if we have defined administrative teams, then set admin flag
                // otherwise leave admin flag unchanged
                if ( isAdmin!=true && !ArrayUtils.isEmpty(admins)) {
                    for (String admin : admins) {
                        if (admin.startsWith("@") && teamName.equalsIgnoreCase(admin.substring(1))) {
                            logger.info(simpleUsername+"is admin");
                            isAdmin = true;
                        }
                    }
                }
            }
        }
        
        if (isAdmin) {
            String groupAdminMemberPattern = settings.getString(Keys.realm.ldap.groupAdminMemberPattern, "(objectClass=group)");
            logger.info("Is admin value "+isAdmin+"add all groups from groupAdminMemberPattern = "+groupAdminMemberPattern);
            teamMembershipResult = searchTeamsInLdap(ldapConnection, groupBase, true, groupAdminMemberPattern, null);
            if (teamMembershipResult != null && teamMembershipResult.getEntryCount() > 0) {
                for (int i = 0; i < teamMembershipResult.getEntryCount(); i++) {
                    SearchResultEntry teamEntry = teamMembershipResult.getSearchEntries().get(i);
                    String teamName = teamEntry.getAttribute("cn").getValue();
                    TeamModel teamModel = userManager.getTeamModel(teamName);
                    if (teamModel == null) {
                        teamModel = createTeamFromLdap(teamEntry);
                    }

                    user.teams.add(teamModel);
                    teamModel.addUser(user.getName());
                }
            }

        }
    }

	private void getEmptyTeamsFromLdap(LdapConnection ldapConnection) {
		logger.info("Start fetching empty teams from ldap.");
		String groupBase = settings.getString(Keys.realm.ldap.groupBase, "");
		String groupMemberPattern = settings.getString(Keys.realm.ldap.groupEmptyMemberPattern, "(&(objectClass=group)(!(member=*)))");

		SearchResult teamMembershipResult = searchTeamsInLdap(ldapConnection, groupBase, true, groupMemberPattern, null);
		if (teamMembershipResult != null && teamMembershipResult.getEntryCount() > 0) {
			for (int i = 0; i < teamMembershipResult.getEntryCount(); i++) {
				SearchResultEntry teamEntry = teamMembershipResult.getSearchEntries().get(i);
				if (!teamEntry.hasAttribute("member")) {
					String teamName = teamEntry.getAttribute("cn").getValue();

					TeamModel teamModel = userManager.getTeamModel(teamName);
					if (teamModel == null) {
						teamModel = createTeamFromLdap(teamEntry);
						setAdminAttribute(teamModel);
						userManager.updateTeamModel(teamModel);
					}
				}
			}
		}
		logger.info("Finished fetching empty teams from ldap.");
	}


	private SearchResult searchTeamsInLdap(LdapConnection ldapConnection, String base, boolean dereferenceAliases, String filter, List<String> attributes) {
		SearchResult result = ldapConnection.search(base, dereferenceAliases, filter, attributes);
		if (result == null) {
			return null;
		}

		if (result.getResultCode() != ResultCode.SUCCESS) {
			// Retry the search with user authorization in case we searched as a manager account that could not search for teams.
			logger.debug("Rebinding as user to search for teams in LDAP");
			result = null;
			if (ldapConnection.rebindAsUser()) {
				result = ldapConnection.search(base, dereferenceAliases, filter, attributes);
				if (result.getResultCode() != ResultCode.SUCCESS) {
					return null;
				}
				logger.info("Successful search after rebinding as user.");
			}
		}

		return result;
	}


	private TeamModel createTeamFromLdap(SearchResultEntry teamEntry) {
		TeamModel answer = new TeamModel(teamEntry.getAttributeValue("cn"));
		answer.accountType = getAccountType();
		// potentially retrieve other attributes here in the future

		return answer;
	}

	private SearchResult doSearch(LdapConnection ldapConnection, String base, String filter) {
		try {
			SearchRequest searchRequest = new SearchRequest(base, SearchScope.SUB, filter);
			SearchResult result = ldapConnection.search(searchRequest);
			if (result.getResultCode() != ResultCode.SUCCESS) {
				return null;
			}
			return result;
		} catch (LDAPException e) {
			logger.error("Problem creating LDAP search", e);
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

	// From: https://www.owasp.org/index.php/Preventing_LDAP_Injection_in_Java
	private static final String escapeLDAPSearchFilter(String filter) {
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

	private void configureSyncService() {
		LdapSyncService ldapSyncService = new LdapSyncService(settings, this);
		if (ldapSyncService.isReady()) {
			long ldapSyncPeriod = getSynchronizationPeriodInMilliseconds();
			int delay = 1;
			logger.info("Ldap sync service will update users and groups every {} minutes.",
					TimeUnit.MILLISECONDS.toMinutes(ldapSyncPeriod));
			scheduledExecutorService.scheduleAtFixedRate(ldapSyncService, delay, ldapSyncPeriod,  TimeUnit.MILLISECONDS);
		} else {
			logger.info("Ldap sync service is disabled.");
		}
	}



	private class LdapConnection {
		private LDAPConnection conn;
		private SimpleBindRequest currentBindRequest;
		private SimpleBindRequest managerBindRequest;
		private SimpleBindRequest userBindRequest;


		public LdapConnection() {
			String bindUserName = settings.getString(Keys.realm.ldap.username, "");
			String bindPassword = settings.getString(Keys.realm.ldap.password, "");
			if (StringUtils.isEmpty(bindUserName) && StringUtils.isEmpty(bindPassword)) {
				this.managerBindRequest = new SimpleBindRequest();
			}
			this.managerBindRequest = new SimpleBindRequest(bindUserName, bindPassword);
		}


		boolean connect() {
			try {
				URI ldapUrl = new URI(settings.getRequiredString(Keys.realm.ldap.server));
				String ldapHost = ldapUrl.getHost();
				int ldapPort = ldapUrl.getPort();

				if (ldapUrl.getScheme().equalsIgnoreCase("ldaps")) {
					// SSL
					SSLUtil sslUtil = new SSLUtil(new TrustAllTrustManager());
					conn = new LDAPConnection(sslUtil.createSSLSocketFactory());
					if (ldapPort == -1) {
						ldapPort = 636;
					}
				} else if (ldapUrl.getScheme().equalsIgnoreCase("ldap") || ldapUrl.getScheme().equalsIgnoreCase("ldap+tls")) {
					// no encryption or StartTLS
					conn = new LDAPConnection();
					 if (ldapPort == -1) {
						 ldapPort = 389;
					 }
				} else {
					logger.error("Unsupported LDAP URL scheme: " + ldapUrl.getScheme());
					return false;
				}

				conn.connect(ldapHost, ldapPort);

				if (ldapUrl.getScheme().equalsIgnoreCase("ldap+tls")) {
					SSLUtil sslUtil = new SSLUtil(new TrustAllTrustManager());
					ExtendedResult extendedResult = conn.processExtendedOperation(
							new StartTLSExtendedRequest(sslUtil.createSSLContext()));
					if (extendedResult.getResultCode() != ResultCode.SUCCESS) {
						throw new LDAPException(extendedResult.getResultCode());
					}
				}

				return true;

			} catch (URISyntaxException e) {
				logger.error("Bad LDAP URL, should be in the form: ldap(s|+tls)://<server>:<port>", e);
			} catch (GeneralSecurityException e) {
				logger.error("Unable to create SSL Connection", e);
			} catch (LDAPException e) {
				logger.error("Error Connecting to LDAP", e);
			}

			return false;
		}


		void close() {
			if (conn != null) {
				conn.close();
			}
		}


		SearchResult search(SearchRequest request) {
			try {
				return conn.search(request);
			} catch (LDAPSearchException e) {
				logger.error("Problem Searching LDAP [{}]",  e.getResultCode());
				return e.getSearchResult();
			}
		}


		SearchResult search(String base, boolean dereferenceAliases, String filter, List<String> attributes) {
			try {
				SearchRequest searchRequest = new SearchRequest(base, SearchScope.SUB, filter);
				if (dereferenceAliases) {
					searchRequest.setDerefPolicy(DereferencePolicy.SEARCHING);
				}
				if (attributes != null) {
					searchRequest.setAttributes(attributes);
				}
				SearchResult result = search(searchRequest);
				return result;

			} catch (LDAPException e) {
				logger.error("Problem creating LDAP search", e);
				return null;
			}
		}



		/**
		 * Bind using the manager credentials set in realm.ldap.username and ..password
		 * @return A bind result, or null if binding failed.
		 */
		BindResult bind() {
			BindResult result = null;
			try {
				result = conn.bind(managerBindRequest);
				currentBindRequest = managerBindRequest;
			} catch (LDAPException e) {
				logger.error("Error authenticating to LDAP with manager account to search the directory.");
				logger.error("  Please check your settings for realm.ldap.username and realm.ldap.password.");
				logger.debug("  Received exception when binding to LDAP", e);
				return null;
			}
			return result;
		}


		/**
		 * Bind using the given credentials, by filling in the username in the given {@code bindPattern} to
		 * create the DN.
		 * @return A bind result, or null if binding failed.
		 */
		BindResult bind(String bindPattern, String simpleUsername, String password) {
			BindResult result = null;
			try {
				String bindUser = StringUtils.replace(bindPattern, "${username}", escapeLDAPSearchFilter(simpleUsername));
				SimpleBindRequest request = new SimpleBindRequest(bindUser, password);
				result = conn.bind(request);
				userBindRequest = request;
				currentBindRequest = userBindRequest;
			} catch (LDAPException e) {
				logger.error("Error authenticating to LDAP with user account to search the directory.");
				logger.error("  Please check your settings for realm.ldap.bindpattern.");
				logger.debug("  Received exception when binding to LDAP", e);
				return null;
			}
			return result;
		}


		boolean rebindAsUser() {
			if (userBindRequest == null || currentBindRequest == userBindRequest) {
				return false;
			}
			try {
				conn.bind(userBindRequest);
				currentBindRequest = userBindRequest;
			} catch (LDAPException e) {
				conn.close();
				logger.error("Error rebinding to LDAP with user account.", e);
				return false;
			}
			return true;
		}


		boolean isAuthenticated(String userDn, String password) {
			verifyCurrentBinding();

			// If the currently bound DN is already the DN of the logging in user, authentication has already happened
			// during the previous bind operation. We accept this and return with the current bind left in place.
			// This could also be changed to always retry binding as the logging in user, to make sure that the
			// connection binding has not been tampered with in between. So far I see no way how this could happen
			// and thus skip the repeated binding.
			// This check also makes sure that the DN in realm.ldap.bindpattern actually matches the DN that was found
			// when searching the user entry.
			String boundDN = currentBindRequest.getBindDN();
			if (boundDN != null && boundDN.equals(userDn)) {
				return true;
			}

			// Bind a the logging in user to check for authentication.
			// Afterwards, bind as the original bound DN again, to restore the previous authorization.
			boolean isAuthenticated = false;
			try {
				// Binding will stop any LDAP-Injection Attacks since the searched-for user needs to bind to that DN
				SimpleBindRequest ubr = new SimpleBindRequest(userDn, password);
				conn.bind(ubr);
				isAuthenticated = true;
				userBindRequest = ubr;
			} catch (LDAPException e) {
				logger.error("Error authenticating user ({})", userDn, e);
			}

			try {
				conn.bind(currentBindRequest);
			} catch (LDAPException e) {
				logger.error("Error reinstating original LDAP authorization (code {}). Team information may be inaccurate for this log in.",
							e.getResultCode(), e);
			}
			return isAuthenticated;
		}



		private boolean verifyCurrentBinding() {
			BindRequest lastBind = conn.getLastBindRequest();
			if (lastBind == currentBindRequest) {
				return true;
			}
			logger.debug("Unexpected binding in LdapConnection. {} != {}", lastBind, currentBindRequest);

			String lastBoundDN = ((SimpleBindRequest)lastBind).getBindDN();
			String boundDN = currentBindRequest.getBindDN();
			logger.debug("Currently bound as '{}', check authentication for '{}'", lastBoundDN, boundDN);
			if (boundDN != null && ! boundDN.equals(lastBoundDN)) {
				logger.warn("Unexpected binding DN in LdapConnection. '{}' != '{}'.", lastBoundDN, boundDN);
				logger.warn("Updated binding information in LDAP connection.");
				currentBindRequest = (SimpleBindRequest)lastBind;
				return false;
			}
			return true;
		}
	}
}
