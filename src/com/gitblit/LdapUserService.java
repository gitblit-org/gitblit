package com.gitblit;

import java.io.File;
import java.util.ArrayList;
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


public class LdapUserService extends ConfigUserService {
	
	public static final Logger logger = LoggerFactory.getLogger(LdapUserService.class);
	
	private IStoredSettings settings;
	private DirContext ctx;
	private IUserService fileUserService;
	
	public LdapUserService() {
		super(new File("."));		// Needs a dummy file
	}
	
	@Override
	public void setup(IStoredSettings settings) {
		this.settings = settings;
		File realmFile = GitBlit.getFileOrFolder(Keys.realm_ldap.alternateConfiguration, "users.conf");
		fileUserService = new ConfigUserService(realmFile);
		
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
	protected void write() {
		// TODO: Write
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
					              settings.getRequiredString(Keys.realm_ldap.userTeamLinkAttributeName)};
		}
		
		@Override
		public UserModel doCallback(SearchResult searchResult) {
			String userNameAttribute = settings.getRequiredString(Keys.realm_ldap.userNameAttribute);
			String adminAttributeName = settings.getRequiredString(Keys.realm_ldap.adminAttributeName);
			String adminAttributeValue = settings.getRequiredString(Keys.realm_ldap.adminAttributeValue);
			String userTeamLinkAttributeName = settings.getRequiredString(Keys.realm_ldap.userTeamLinkAttributeName);
			String userTeamLinkAttributeRegex = settings.getRequiredString(Keys.realm_ldap.userTeamLinkAttributeRegex);
			Integer userTeamLinkAttributeRegexGroup = settings.getInteger(Keys.realm_ldap.userTeamLinkAttributeRegexGroup, 0);
			
			Attributes nodeAttributes = searchResult.getAttributes();
			
			try {
				UserModel answer = new UserModel(nodeAttributes.get(userNameAttribute).get().toString());
				
				// Get the admin attribute
				Attribute adminAttribute = nodeAttributes.get(adminAttributeName);
				for (int i = 0; i < adminAttribute.size(); i++) {
					if (adminAttribute.get(i).toString().equals(adminAttributeValue))
						answer.canAdmin = true;
				}
				
				// Get the repositories for this User
				if (isUserRepositoryLinkInLdap())
					throw new IllegalArgumentException("LDAP Lookup of repositories not implemented"); // TODO: Implement User->Repository lookup in LDAP
				else {
					UserModel fileUserModel = fileUserService.getUserModel(answer.getName());
					if (fileUserModel != null && fileUserModel.repositories != null)
						answer.repositories.addAll(fileUserModel.repositories);
				}
				
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
				} else {
					UserModel fileUserModel = fileUserService.getUserModel(answer.getName());
					if (fileUserModel != null && fileUserModel.teams != null)
						answer.teams.addAll(fileUserModel.teams);
				}
				
				// TODO: excludeFromFederation
				
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
					              settings.getRequiredString(Keys.realm_ldap.teamUserLinkAttributeName) };
		}
		
		@Override
		public TeamModel doCallback(SearchResult searchResult) {
			String teamNameAttribute = settings.getRequiredString(Keys.realm_ldap.teamNameAttribute);
			String teamUserLinkAttributeName = settings.getRequiredString(Keys.realm_ldap.teamUserLinkAttributeName);
			String teamUserLinkAttributeRegex = settings.getRequiredString(Keys.realm_ldap.teamUserLinkAttributeRegex);
			Integer teamUserLinkAttributeRegexGroup = settings.getInteger(Keys.realm_ldap.teamUserLinkAttributeRegexGroup, 0);
			
			Attributes nodeAttributes = searchResult.getAttributes();
			
			try {
				TeamModel answer = new TeamModel(nodeAttributes.get(teamNameAttribute).get().toString());
				
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
				else {
					TeamModel fileTeamModel = fileUserService.getTeamModel(answer.name);
					if (fileTeamModel != null && fileTeamModel.users != null)
						answer.users.addAll(fileTeamModel.users);
				}
				
				// Get the repositories for this team
				if (isTeamRepositoryLinkInLdap())
					throw new IllegalArgumentException("LDAP Lookup of repositories not implemented"); // TODO: Implement Team->Repository lookup in LDAP
				else {
					TeamModel fileTeamModel = fileUserService.getTeamModel(answer.name);
					if (fileTeamModel != null && fileTeamModel.repositories != null)
						answer.repositories.addAll(fileTeamModel.repositories);
				}
				
				// TODO: Get mailingLists
				// TODO: Get preReceiveScripts
				// TODO: Get postReceiveScripts
				
				return answer;
			} catch (NamingException e) {
				logger.error("Problems converting search result to team object");
			}
			
			return null;
		}
	};
	
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

}
