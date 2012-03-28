package com.gitblit;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.models.TeamModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.DeepCopier;


public class LdapUserService extends ConfigUserService {
	
	public static final Logger logger = LoggerFactory.getLogger(LdapUserService.class);
	
	private IStoredSettings settings;
	private DirContext ctx;
	private IUserService fileUserService;
	private long lastRefreshed = 0;
	private long refreshInterval = 0;
	
	public LdapUserService() {
		super(new File("."));		// Needs a dummy file
	}
	
	@Override
	public void setup(IStoredSettings settings) {
		this.settings = settings;
		String alternateConfiguration = settings.getString(Keys.realm_ldap.alternateConfiguration, "users.conf");
		File realmFile = GitBlit.getFileOrFolder(alternateConfiguration);
		fileUserService = new GitblitUserService(realmFile);
		this.refreshInterval = settings.getInteger(Keys.realm_ldap.refreshInterval, 3600) * 60 * 1000;
		
		this.ctx = getLdapDirContext(settings.getRequiredString(Keys.realm_ldap.principal), settings.getRequiredString(Keys.realm_ldap.credentials));
	}
	
	protected DirContext getLdapDirContext(String principal, String credentials) {
		try {
			Hashtable<String, String> env = new Hashtable<String, String>();
			env.put(Context.INITIAL_CONTEXT_FACTORY,"com.sun.jndi.ldap.LdapCtxFactory");
			env.put(Context.PROVIDER_URL, settings.getRequiredString(Keys.realm_ldap.serverUrl));
			env.put(Context.SECURITY_AUTHENTICATION, settings.getString(Keys.realm_ldap.authenticationType, "simple"));
			env.put(Context.SECURITY_PRINCIPAL, principal);
			env.put(Context.SECURITY_CREDENTIALS, credentials);
			
			return new InitialDirContext(env);
		} catch (NamingException e) {
			logger.debug("Error connecting to LDAP with credentials, could be a bad login", e);
			
			return null;
		}
	}
	
	@Override
	public UserModel authenticate(String username, char[] password) {
		String domainName = settings.getString(Keys.realm_ldap.domainName, "");
		if (domainName.trim().length() > 0)
			username = domainName + "\\" + username;
		
		DirContext ctx = getLdapDirContext(username, new String(password));
		if (ctx != null) {
			try {
				ctx.close();
			} catch (NamingException e) {
				logger.error("Can not close context", e);
			}
			
			// Strip any \ from the username
			int lastSlash = username.lastIndexOf('\\');
			if (lastSlash > -1)
				username = username.substring(lastSlash + 1);			
			
			return getUserModel(username);
		}
		
		return null;
		
	}
	
	@Override
	protected synchronized void read() {
		if (System.currentTimeMillis() > refreshInterval + lastRefreshed) {
			users.clear();
			teams.clear();
			
			readAllUsersFromLdap();
			readAllTeamsFromLdap();
			
			// Push Teams into Users
			for (UserModel userModel : users.values()) {
				Set<TeamModel> allTeams = new HashSet<TeamModel>();
				for (TeamModel teamModel : userModel.teams) {
					TeamModel model = teams.get(teamModel.name.toLowerCase());
					
					if (model != null)		// We might have read a team membership that is not part of the teams we're interested in
						allTeams.add(model);
				}
				
				userModel.teams.clear();
				userModel.teams.addAll(allTeams);
			}
			
			lastRefreshed = System.currentTimeMillis();
		}
	}
	
	/**
	 * Reads all the users from LDAP or the backing file - depending on the realm.ldap.isUsersInLdap
	 * setting
	 */
	protected void readAllUsersFromLdap() {
		List<UserModel> allUsers;
		
		if (isUsersInLdap()) {
			String searchFromNode = settings.getRequiredString(Keys.realm_ldap.usersRootNodeDn);
			String searchCriteria = settings.getRequiredString(Keys.realm_ldap.allUsersSearchCriteria);
			Object[] searchCriteriaArgs = {  };
			
			allUsers = searchLdap(searchFromNode, searchCriteria, searchCriteriaArgs, userModelConverter);
		} else {
			allUsers = fileUserService.getAllUsers();
		}
		
		for (UserModel model : allUsers)
			users.put(model.getName().toLowerCase(), model);
	}
	
	/**
	 * Reads all the users from LDAP or the backing file - depending on the realm.ldap.isUsersInLdap
	 * setting
	 */
	protected void readAllTeamsFromLdap() {
		List<TeamModel> allTeams;
		
		if (isTeamsInLdap()) {
			String searchFromNode = settings.getRequiredString(Keys.realm_ldap.teamsRootNodeDn);
			String searchCriteria = settings.getRequiredString(Keys.realm_ldap.allTeamsSearchCriteria);
			Object[] searchCriteriaArgs = {  };
			
			allTeams = searchLdap(searchFromNode, searchCriteria, searchCriteriaArgs, teamModelConverter);
		} else {
			allTeams = fileUserService.getAllTeams();
		}
		
		for (TeamModel model : allTeams)
			teams.put(model.name.toLowerCase(), model);
	}
	
	@Override
	protected void write() throws IOException {
		// Push the users down
		//fileUserService.users.clear();
		for (UserModel userModel : users.values()) {
			UserModel copy = DeepCopier.copy(userModel);
			if (isUserRepositoryLinkInLdap())
				copy.repositories.clear();
			if (isTeamUserLinkInLdap())
				copy.teams.clear();
			if (isAdminAttributeInLdap())
				copy.canAdmin = false;
			if (isExcludeFromFederationInLdap())
				copy.excludeFromFederation = false;
			
			copy.password = null;		// Password is ALWAYS going against LDAP
			
			fileUserService.updateUserModel(copy);
			//fileUserService.users.put(copy.getName().toLowerCase(), copy);
		}
		
		// Push the teams down
		//fileUserService.teams.clear();
		for (TeamModel teamModel : teams.values()) {
			TeamModel copy = DeepCopier.copy(teamModel);
			if (isTeamUserLinkInLdap())
				copy.users.clear();
			if (isTeamRepositoryLinkInLdap())
				copy.repositories.clear();
			
			fileUserService.updateTeamModel(copy);
			//fileUserService.teams.put(copy.name.toLowerCase(), copy);
		}
		
		//fileUserService.write();
	}
	
	/* LDAP Search Helper Methods */
	
	public static interface LdapSearchCallback<T> {
		public T doCallback(SearchResult searchResult);
		public String[] getRequestedAttributes();
	}
	
	private <T> List<T> searchLdap(String searchFromNode, String searchCriteria, Object[] searchCriteriaArgs, LdapSearchCallback<T> callback) {
		List<T> answer = new ArrayList<T>();
		
		SearchControls ctls = new SearchControls();
		ctls.setReturningObjFlag(true);
		
		boolean subtreeSearch = settings.getBoolean(Keys.realm_ldap.isSubtreeSearch, true);
		if (subtreeSearch)
			ctls.setSearchScope(SearchControls.SUBTREE_SCOPE);
		
		String[] requestedAttributes = callback.getRequestedAttributes();
		ctls.setReturningAttributes(requestedAttributes);
		
		try {
			NamingEnumeration<SearchResult> searchResults = ctx.search(searchFromNode, searchCriteria, searchCriteriaArgs, ctls);
			
			while (searchResults.hasMore())
				answer.add(callback.doCallback(searchResults.next()));
			
		} catch (NamingException e) {
			logger.error("Error executing search", e);
		}
		
		return answer;
	}
	
	public LdapSearchCallback<UserModel> userModelConverter = new LdapSearchCallback<UserModel>() {
		
		@Override
		public String[] getRequestedAttributes() {
			return new String[] { settings.getString(Keys.realm_ldap.userNameAttribute, null), 
					              settings.getString(Keys.realm_ldap.adminAttributeName, null),
					              settings.getRequiredString(Keys.realm_ldap.userTeamLinkAttributeName),
					              settings.getString(Keys.realm_ldap.excludeFromFederationAttributeName, null)};
		}
		
		@Override
		public UserModel doCallback(SearchResult searchResult) {
			String userNameAttribute = settings.getRequiredString(Keys.realm_ldap.userNameAttribute);
			String adminAttributeName = settings.getString(Keys.realm_ldap.adminAttributeName, null);
			String adminAttributeValue = settings.getString(Keys.realm_ldap.adminAttributeValue, null);
			String excludeFromFederationAttributeName = settings.getString(Keys.realm_ldap.excludeFromFederationAttributeName, null);
			String excludeFromFederationAttributeValue = settings.getString(Keys.realm_ldap.excludeFromFederationAttributeValue, null);
			String userTeamLinkAttributeName = settings.getRequiredString(Keys.realm_ldap.userTeamLinkAttributeName);
			String userTeamLinkAttributeRegex = settings.getRequiredString(Keys.realm_ldap.userTeamLinkAttributeRegex);
			Integer userTeamLinkAttributeRegexGroup = settings.getInteger(Keys.realm_ldap.userTeamLinkAttributeRegexGroup, 0);
			
			Attributes nodeAttributes = searchResult.getAttributes();
			
			try {
				String userName = nodeAttributes.get(userNameAttribute).get().toString();
				UserModel answer = fileUserService.getUserModel(userName);
				if (answer == null)
					answer = new UserModel(userName);
				
				// Get the admin attribute
				if (isAdminAttributeInLdap())
					answer.canAdmin = getBooleanAttributeFromLdap(nodeAttributes, adminAttributeName, adminAttributeValue);
				
				// Get the Exclude from federation attribute
				if (isExcludeFromFederationInLdap())
					answer.excludeFromFederation = getBooleanAttributeFromLdap(nodeAttributes, excludeFromFederationAttributeName, excludeFromFederationAttributeValue);
				
				// Get the repositories for this User
				if (isUserRepositoryLinkInLdap())
					throw new IllegalArgumentException("LDAP Lookup of repositories not implemented"); // TODO: Implement User->Repository lookup in LDAP
				
				// Get the teams that this user belongs to
				if (isTeamUserLinkInLdap()) {
					Attribute userTeamLinkAttribute = nodeAttributes.get(userTeamLinkAttributeName);
					for (int i = 0; i < userTeamLinkAttribute.size(); i++) {
						String userTeamLinkAttributeValueToCheck = userTeamLinkAttribute.get(i).toString();
						String teamName = matchRegularExpression(userTeamLinkAttributeRegex, userTeamLinkAttributeValueToCheck, userTeamLinkAttributeRegexGroup);
						if (teamName != null) {
							answer.teams.add(new TeamModel(teamName));		// Add a shell of a team model - to be filled in later (performance)
						}
					}
				}
				
				return answer;
			} catch (NamingException e) {
				logger.error("Problems converting search result to user object");
			}
			
			return null;
		}
	};
	
	public LdapSearchCallback<TeamModel> teamModelConverter = new LdapSearchCallback<TeamModel>() {
		@Override
		public String[] getRequestedAttributes() {
			return new String[] { settings.getRequiredString(Keys.realm_ldap.teamNameAttribute),
					              settings.getRequiredString(Keys.realm_ldap.teamUserLinkAttributeName),
					              settings.getString(Keys.realm_ldap.mailingListsAttributeName, null),
					              settings.getString(Keys.realm_ldap.preReceiveScriptsAttributeName, null),
					              settings.getString(Keys.realm_ldap.postReceiveScriptsAttributeName, null)};
		}
		
		@Override
		public TeamModel doCallback(SearchResult searchResult) {
			String teamNameAttribute = settings.getRequiredString(Keys.realm_ldap.teamNameAttribute);
			String teamUserLinkAttributeName = settings.getRequiredString(Keys.realm_ldap.teamUserLinkAttributeName);
			String teamUserLinkAttributeRegex = settings.getRequiredString(Keys.realm_ldap.teamUserLinkAttributeRegex);
			Integer teamUserLinkAttributeRegexGroup = settings.getInteger(Keys.realm_ldap.teamUserLinkAttributeRegexGroup, 0);
			String mailingListsAttributeName = settings.getString(Keys.realm_ldap.mailingListsAttributeName, null);
			String preReceiveScriptsAttributeName = settings.getString(Keys.realm_ldap.preReceiveScriptsAttributeName, null);
			String postReceiveScriptsAttributeName = settings.getString(Keys.realm_ldap.postReceiveScriptsAttributeName, null);
			
			Attributes nodeAttributes = searchResult.getAttributes();
			
			try {
				String teamName = nodeAttributes.get(teamNameAttribute).get().toString();
				TeamModel answer = fileUserService.getTeamModel(teamName);
				if (answer == null)
					answer = new TeamModel(teamName);
				
				// Get the users that belong to this team
				if (isTeamUserLinkInLdap()) {
					Attribute userTeamLinkAttribute = nodeAttributes.get(teamUserLinkAttributeName);
					for (int i = 0; i < userTeamLinkAttribute.size(); i++) {
						String userTeamLinkAttributeValueToCheck = userTeamLinkAttribute.get(i).toString();
						String userName = matchRegularExpression(teamUserLinkAttributeRegex, userTeamLinkAttributeValueToCheck, teamUserLinkAttributeRegexGroup);
						if (userName != null) {
							answer.addUser(userName);
						}
					}
				}
				
				// Get the repositories for this team
				if (isTeamRepositoryLinkInLdap())
					throw new IllegalArgumentException("LDAP Lookup of repositories not implemented"); // TODO: Implement Team->Repository lookup in LDAP
				
				// Get mailingLists
				if (isMailingListsInLdap())
					answer.mailingLists.addAll(getCommaSeparatedStringListAttributeFromLdap(nodeAttributes, mailingListsAttributeName));
				
				// Get preReceiveScripts
				if (isPreReceiveScriptInLdap())
					answer.preReceiveScripts.addAll(getCommaSeparatedStringListAttributeFromLdap(nodeAttributes, preReceiveScriptsAttributeName));
				
				// Get postReceiveScripts
				if (isPostReceiveScriptInLdap())
					answer.postReceiveScripts.addAll(getCommaSeparatedStringListAttributeFromLdap(nodeAttributes, postReceiveScriptsAttributeName));
				
				return answer;
			} catch (NamingException e) {
				logger.error("Problems converting search result to team object");
			}
			
			return null;
		}

		
	};
	
	private Collection<String> getCommaSeparatedStringListAttributeFromLdap(Attributes nodeAttributes, String attributeName) throws NamingException {
		if (attributeName != null && attributeName.trim().length() > 0) {
			if (nodeAttributes.get(attributeName) != null) {
				Object attributeValue = nodeAttributes.get(attributeName).get();
				if (attributeValue != null) {
					return Arrays.asList(attributeValue.toString().split(","));
					
				}
			}
		}
		
		return Collections.EMPTY_LIST;
	}
	
	private boolean getBooleanAttributeFromLdap(Attributes nodeAttributes, String attributeName, String attributeValue) throws NamingException {
		if (attributeName != null && attributeName.trim().length() > 0) {
			Attribute attribute = nodeAttributes.get(attributeName);
			for (int i = 0; i < attribute.size(); i++) {
				if (attribute.get(i).toString().equals(attributeValue))
					return true;
			}
		}
	
		return false;
	}
	
	private String matchRegularExpression(String pattern, String toCheck, Integer groupToReturn) {
		Pattern p = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
		Matcher m = p.matcher(toCheck);
		if (m.find()) {
			return m.group(groupToReturn);
		}
		
		return null;
	}
	
	protected boolean isUsersInLdap() {
		return settings.getBoolean(Keys.realm_ldap.isUsersInLdap, true);
	}
	
	protected boolean isTeamsInLdap() {
		return settings.getBoolean(Keys.realm_ldap.isTeamsInLdap, true);
	}
	
	protected boolean isUserRepositoryLinkInLdap() {
		return settings.getBoolean(Keys.realm_ldap.isUserRepositoryLinkInLdap, true);
	}
	
	protected boolean isTeamUserLinkInLdap() {
		return settings.getBoolean(Keys.realm_ldap.isTeamUserLinkInLdap, true);
	}
	
	protected boolean isTeamRepositoryLinkInLdap() {
		return settings.getBoolean(Keys.realm_ldap.isTeamRepositoryLinkInLdap, true);
	}
	
	protected boolean isAdminAttributeInLdap() {
		return isSettingPresent(Keys.realm_ldap.adminAttributeName);
	}
	
	protected boolean isExcludeFromFederationInLdap() {
		return isSettingPresent(Keys.realm_ldap.excludeFromFederationAttributeName);
	}
	
	protected boolean isMailingListsInLdap() {
		return isSettingPresent(Keys.realm_ldap.mailingListsAttributeName);
	}
	
	protected boolean isPreReceiveScriptInLdap() {
		return isSettingPresent(Keys.realm_ldap.preReceiveScriptsAttributeName);
	}
	
	protected boolean isPostReceiveScriptInLdap() {
		return isSettingPresent(Keys.realm_ldap.postReceiveScriptsAttributeName);
	}
	
	protected boolean isSettingPresent(String setting) {
		String settingValue = settings.getString(setting, null);
		return settingValue != null && settingValue.trim().length() > 0;
	}

}
