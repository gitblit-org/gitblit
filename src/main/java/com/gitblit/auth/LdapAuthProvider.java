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
import java.util.regex.Pattern;

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

  private static final Pattern syncDelimiter = Pattern.compile(" ");
  private final ScheduledExecutorService scheduledExecutorService;

  public LdapAuthProvider() {
    super("ldap");

    scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
  }

  private long getSynchronizationPeriodInMilliseconds() {

    long syncInterval = 0L;
    String period = null;

    try {
      if (null != settings) {

        // Get the sync interval
        settings.getString(Keys.realm.ldap.syncPeriod, null);

        if (StringUtils.isEmpty(period)) {

          // If the sync interval cannot is not set in the default.properties or gitblit.properties file, try using the deprecated realm.ldap.ldapCachePeriod property.
          period = settings.getString("realm.ldap.ldapCachePeriod", null);

          if (StringUtils.isEmpty(period)) {

            // If the deprecated realm.ldap.ldapCachePeriod property cannot be found, set the sync interval to 5 min.
            period = "5 MINUTES";
          } else {
            logger.warn("realm.ldap.ldapCachePeriod is obsolete!");
            logger.warn(MessageFormat.format("Please set {0}={1} in gitblit.properties!", Keys.realm.ldap.syncPeriod, period));
            settings.overrideSetting(Keys.realm.ldap.syncPeriod, period);
          }
        }
      }
    } catch (Exception ex) {
      logger.error("An unexpected error occured while trying to read the property [" + Keys.realm.ldap.syncPeriod + "]", ex);
    } finally {
      syncInterval = this.getSynchronizationInterval(period);
    }

    return syncInterval;

  }

  private long getSynchronizationInterval(String period) {

    long duration = 0;
    TimeUnit timeUnit = null;

    // Set the default sync interval to 5 minutes;
    long syncInterval = 5 * 60 * 1000;

    try {
      final String[] synyIntervalData = syncDelimiter.split(period);
      duration = Math.abs(Long.parseLong(synyIntervalData[0]));
      timeUnit = TimeUnit.valueOf(synyIntervalData[1]);
      syncInterval = timeUnit.toMillis(duration);
    } catch (RuntimeException ex) {
      logger.error(Keys.realm.ldap.syncPeriod
          + " must have format '<long> <TimeUnit>' where <TimeUnit> is one of 'MILLISECONDS', 'SECONDS', 'MINUTES', 'HOURS', 'DAYS'.  Defaulting to 5 Minutes", ex);
    }

    return syncInterval;
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
              Attribute uid = loggingInUser.getAttribute(uidAttribute);
              if (uid == null) {
                logger.error("Can not synchronize with LDAP, missing \"{}\" attribute", uidAttribute);
                continue;
              }
              final String username = uid.getValue();
              logger.debug("LDAP synchronizing: " + username);

              UserModel user = userManager.getUserModel(username);
              logger.debug("User Manager Type: " + userManager.getClass().getName());

              if (user == null) {
                user = new UserModel(username);
              }
              logger.debug("User: " + user);

              logger.debug("Getting teams from LDAP.");
              if (!supportsTeamMembershipChanges()) {
                logger.debug("Getting teams from LDAP.");
                getTeamsFromLdap(ldapConnection, username, loggingInUser, user);
                logger.debug("Found teams: " + user.getTeams());
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
                for (TeamModel userTeam : user.getTeams()) {
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

  private LDAPConnection getLdapConnection() {
    try {

      URI ldapUrl = new URI(settings.getRequiredString(Keys.realm.ldap.server));
      logger.debug("ldapUrl: " + ldapUrl);

      String ldapHost = ldapUrl.getHost();
      logger.debug("ldapHost: " + ldapHost);

      int ldapPort = ldapUrl.getPort();
      logger.debug("ldapPort: " + ldapPort);

      String bindUserName = settings.getString(Keys.realm.ldap.username, "");
      logger.debug("bindUserName: " + bindUserName);

      String bindPassword = settings.getString(Keys.realm.ldap.password, "");
      logger.debug("bindPassword: " + bindPassword);

      LDAPConnection conn = null;
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
      }

      logger.debug("LDAP Connection is: " + conn);
      if (null != conn) {

        logger.debug("LDAP Connecting to [" + ldapHost + ":" + ldapPort + "].");
        conn.connect(ldapHost, ldapPort);
        logger.debug("LDAP Connected to [" + ldapHost + ":" + ldapPort + "].");

        if (ldapUrl.getScheme().equalsIgnoreCase("ldap+tls")) {
          SSLUtil sslUtil = new SSLUtil(new TrustAllTrustManager());
          ExtendedResult extendedResult = conn.processExtendedOperation(new StartTLSExtendedRequest(sslUtil.createSSLContext()));
          if (extendedResult.getResultCode() != ResultCode.SUCCESS) {
            throw new LDAPException(extendedResult.getResultCode());
          }
        }

        logger.debug("LDAP binding to [" + bindUserName + ":" + bindPassword + "].");
        if (StringUtils.isEmpty(bindUserName) && StringUtils.isEmpty(bindPassword)) {
          // anonymous bind
          conn.bind(new SimpleBindRequest());
        } else {
          // authenticated bind
          conn.bind(new SimpleBindRequest(bindUserName, bindPassword));
        }
        logger.debug("LDAP complete binding to [" + bindUserName + ":" + bindPassword + "].");
      }
      return conn;

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
   * Credentials are defined in the LDAP server and can not be manipulated from Gitblit.
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
   * If the LDAP server will maintain team memberships then LdapUserService will not allow team membership changes. In this scenario all team changes must be made on the LDAP
   * server by the LDAP administrator.
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
        List<String> admins = settings.getStrings(Keys.realm.ldap.admins);
        if (admins.contains(user.username)) {
          return false;
        }
      }
    }
    return true;
  }

  @Override
  public boolean supportsRoleChanges(TeamModel team, Role role) {
    if (Role.ADMIN == role) {
      if (!supportsTeamMembershipChanges()) {
        List<String> admins = settings.getStrings(Keys.realm.ldap.admins);
        if (admins.contains("@" + team.name)) {
          return false;
        }
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

    LDAPConnection ldapConnection = getLdapConnection();
    if (ldapConnection != null) {
      try {
        boolean alreadyAuthenticated = false;

        String bindPattern = settings.getString(Keys.realm.ldap.bindpattern, "");
        if (!StringUtils.isEmpty(bindPattern)) {
          try {
            String bindUser = StringUtils.replace(bindPattern, "${username}", escapeLDAPSearchFilter(simpleUsername));
            ldapConnection.bind(bindUser, new String(password));

            alreadyAuthenticated = true;
          } catch (LDAPException e) {
            return null;
          }
        }

        // Find the logging in user's DN
        String accountBase = settings.getString(Keys.realm.ldap.accountBase, "");
        String accountPattern = settings.getString(Keys.realm.ldap.accountPattern, "(&(objectClass=person)(sAMAccountName=${username}))");
        accountPattern = StringUtils.replace(accountPattern, "${username}", escapeLDAPSearchFilter(simpleUsername));

        SearchResult result = doSearch(ldapConnection, accountBase, accountPattern);
        if (result != null && result.getEntryCount() == 1) {
          SearchResultEntry loggingInUser = result.getSearchEntries().get(0);
          String loggingInUserDN = loggingInUser.getDN();

          if (alreadyAuthenticated || isAuthenticated(ldapConnection, loggingInUserDN, new String(password))) {
            logger.debug("LDAP authenticated: " + username);

            UserModel user = null;
            synchronized (this) {
              user = userManager.getUserModel(simpleUsername);
              if (user == null) {
                // create user object for new authenticated user
                user = new UserModel(simpleUsername);
              }

              // create a user cookie
              setCookie(user, password);

              if (!supportsTeamMembershipChanges()) {
                getTeamsFromLdap(ldapConnection, simpleUsername, loggingInUser, user);
              }

              // Get User Attributes
              setUserAttributes(user, loggingInUser);

              // Push the ldap looked up values to backing file
              updateUser(user);

              if (!supportsTeamMembershipChanges()) {
                for (TeamModel userTeam : user.getTeams()) {
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
   * Set the admin attribute from team memberships retrieved from LDAP. If we are not storing teams in LDAP and/or we have not defined any administrator teams, then do not change
   * the admin flag.
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
          if (admin.startsWith("@") && user.isTeamMember(admin.substring(1))) {
            // admin team
            user.canAdmin = true;
          } else if (user.getName().equalsIgnoreCase(admin)) {
            // admin user
            user.canAdmin = true;
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

  private void getTeamsFromLdap(LDAPConnection ldapConnection, String simpleUsername, SearchResultEntry loggingInUser, UserModel user) {

    logger.debug("--------------------------------------------------------------------");
    logger.debug("------------------------ Getting LDAP Teams ------------------------");
    logger.debug("--------------------------------------------------------------------");
    String loggingInUserDN = loggingInUser.getDN();

    // Clear the users team memberships - we're going to get them from LDAP
    user.removeAllTeams();
    logger.debug("Removed all teams from user: " + user);

    String groupBase = settings.getString(Keys.realm.ldap.groupBase, "");

    String groupMemberPattern = settings.getString(Keys.realm.ldap.groupMemberPattern, "(&(objectClass=group)(member=${dn}))");
    groupMemberPattern = StringUtils.replace(groupMemberPattern, "${dn}", escapeLDAPSearchFilter(loggingInUserDN));
    groupMemberPattern = StringUtils.replace(groupMemberPattern, "${username}", escapeLDAPSearchFilter(simpleUsername));

    // Fill in attributes into groupMemberPattern
    logger.debug("User Attributes: " + loggingInUser.getAttributes());
    for (Attribute userAttribute : loggingInUser.getAttributes()) {
      groupMemberPattern = StringUtils.replace(groupMemberPattern, "${" + userAttribute.getName() + "}", escapeLDAPSearchFilter(userAttribute.getValue()));
    }

    logger.debug("ldap connection: " + ldapConnection);
    logger.debug("groupBase: " + groupBase);
    logger.debug("groupMemberPattern: " + groupMemberPattern);
    SearchResult teamMembershipResult = doSearch(ldapConnection, groupBase, true, groupMemberPattern, Arrays.asList("cn"));
    logger.debug("teamMembershipResult: " + teamMembershipResult);

    if (teamMembershipResult != null && teamMembershipResult.getEntryCount() > 0) {

      logger.debug("teamMembershipResult count: " + teamMembershipResult.getEntryCount());

      for (int i = 0; i < teamMembershipResult.getEntryCount(); i++) {

        SearchResultEntry teamEntry = teamMembershipResult.getSearchEntries().get(i);
        logger.debug("teamEntry: " + teamEntry);

        String teamName = teamEntry.getAttribute("cn").getValue();
        logger.debug("teamName: " + teamName);

        TeamModel teamModel = userManager.getTeamModel(teamName);
        logger.debug("teamModel from team name: " + teamName);

        if (teamModel == null) {
          teamModel = createTeamFromLdap(teamEntry);
          logger.debug("ldap teamModel: " + teamName);
        }

        user.addTeam(teamModel);
        logger.debug("Added team [" + teamModel + "] to user [" + user + "].");

        teamModel.addUser(user.getName());
        logger.debug("Added user [" + user + "] to team [" + teamModel + "].");
      }
    }

    logger.debug("--------------------------------------------------------------------");
  }

  private void getEmptyTeamsFromLdap(LDAPConnection ldapConnection) {
    logger.info("Start fetching empty teams from ldap.");
    String groupBase = settings.getString(Keys.realm.ldap.groupBase, "");
    String groupMemberPattern = settings.getString(Keys.realm.ldap.groupEmptyMemberPattern, "(&(objectClass=group)(!(member=*)))");

    SearchResult teamMembershipResult = doSearch(ldapConnection, groupBase, true, groupMemberPattern, null);
    if (teamMembershipResult != null && teamMembershipResult.getEntryCount() > 0) {
      for (int i = 0; i < teamMembershipResult.getEntryCount(); i++) {
        SearchResultEntry teamEntry = teamMembershipResult.getSearchEntries().get(i);
        if (!teamEntry.hasAttribute("member")) {
          String teamName = teamEntry.getAttribute("cn").getValue();

          TeamModel teamModel = userManager.getTeamModel(teamName);
          if (teamModel == null) {
            teamModel = createTeamFromLdap(teamEntry);
            userManager.updateTeamModel(teamModel);
          }
        }
      }
    }
    logger.info("Finished fetching empty teams from ldap.");
  }

  private TeamModel createTeamFromLdap(SearchResultEntry teamEntry) {
    TeamModel answer = new TeamModel(teamEntry.getAttributeValue("cn"));
    answer.accountType = getAccountType();
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

  private SearchResult doSearch(LDAPConnection ldapConnection, String base, boolean dereferenceAliases, String filter, List<String> attributes) {
    try {
      SearchRequest searchRequest = new SearchRequest(base, SearchScope.SUB, filter);
      if (dereferenceAliases) {
        searchRequest.setDerefPolicy(DereferencePolicy.SEARCHING);
      }
      if (attributes != null) {
        searchRequest.setAttributes(attributes);
      }

      logger.debug("base: " + base);
      logger.debug("SearchScope.SUB: " + SearchScope.SUB);
      logger.debug("filter: " + filter);
      logger.debug("searchRequest: " + searchRequest);
      logger.debug("ldapConnection is connected: " + ldapConnection.isConnected());
      return ldapConnection.search(searchRequest);

    } catch (LDAPSearchException e) {
      logger.error("Problem Searching LDAP", e);

      return null;
    } catch (LDAPException e) {
      logger.error("Problem creating LDAP search", e);
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

  private void configureSyncService() {
    LdapSyncService ldapSyncService = new LdapSyncService(settings, this);
    if (ldapSyncService.isReady()) {
      long ldapSyncPeriod = getSynchronizationPeriodInMilliseconds();
      int delay = 1;
      logger.info("Ldap sync service will update users and groups every {} minutes.", TimeUnit.MILLISECONDS.toMinutes(ldapSyncPeriod));
      scheduledExecutorService.scheduleAtFixedRate(ldapSyncService, delay, ldapSyncPeriod, TimeUnit.MILLISECONDS);
    } else {
      logger.info("Ldap sync service is disabled.");
    }
  }

}
