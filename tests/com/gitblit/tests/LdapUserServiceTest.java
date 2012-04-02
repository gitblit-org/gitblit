package com.gitblit.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.SearchResult;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.gitblit.GitBlit;
import com.gitblit.LdapUserService;
import com.gitblit.models.TeamModel;
import com.gitblit.models.UserModel;
import com.gitblit.tests.mock.MemorySettings;
import com.gitblit.tests.mock.MockDirContext;

/*
 * A test case covering the following setup (LDAP Is Mocked):
 * 
 * Users -> Persisted in LDAP
 *   - Repo List persisted in users.conf
 * 
 * Teams -> Persisted in LDAP
 *   - Repo List persisted in users.conf
 *   - User List persisted in LDAP
 */
public class LdapUserServiceTest {
	
	private LdapUserService ldapUserService;
	MemorySettings settings;
	
	@Before
	public void setup() throws Exception {
		GitBlit gitBlit = new GitBlit();
		
		// Mock out the backing configuration
		File f = GitBlit.getFileOrFolder("ldapUserServiceTest.conf");
		if (f.createNewFile()) {
			BufferedWriter out = new BufferedWriter(new FileWriter("ldapUserServiceTest.conf"));
			out.write("[team \"Git_Users\"]\n");
			out.write("\trepository = helloworld.git\n");
			out.write("\trepository = repoTwo.git\n");
			out.write("\trepository = myRepos/nestedRepo.git\n");
			out.write("\tpreReceiveScript = testscript\n");
			out.write("\tpreReceiveScript = jenkins\n");
			out.write("\tpostReceiveScript = postReceiveOne\n");
			out.write("\tpostReceiveScript = postReceiveTwo\n");
			out.write("[user \"jcrygier\"]\n");
			out.write("\trepository = repothree.git\n");
			out.write("\trole = \"#notfederated\"\n");
			
			out.flush();
			out.close();
		}
		
		Map<Object, Object> props = new HashMap<Object, Object>();
		props.put("realm.ldap.alternateConfiguration", "ldapUserServiceTest.conf");
		props.put("realm.ldap.serverUrl", "notneeded");
		props.put("realm.ldap.principal", "jcrygier");
		props.put("realm.ldap.credentials", "mocked");
		props.put("realm.ldap.usersRootNodeDn", "OU=User_Accounts,OU=User_Control,OU=myOrganization,DC=myCompany");
		props.put("realm.ldap.allUsersSearchCriteria", "(objectClass=user)");
		props.put("realm.ldap.teamsRootNodeDn", "OU=Security_Groups,OU=User_Control,OU=myOrganization,DC=myCompany");
		props.put("realm.ldap.allTeamsSearchCriteria", "(&(objectClass=group)(cn=git*))");
		props.put("realm.ldap.userNameAttribute", "name");
		props.put("realm.ldap.adminAttributeName", "memberOf");
		props.put("realm.ldap.adminAttributeValue", "CN=Git_Admins,OU=Security_Groups,OU=User_Control,OU=myOrganization,DC=myCompany");
		props.put("realm.ldap.teamNameAttribute", "name");
		props.put("realm.ldap.isUserRepositoryLinkInLdap", "false");
		props.put("realm.ldap.isTeamRepositoryLinkInLdap", "false");
		props.put("realm.ldap.userTeamLinkAttributeName", "memberOf");
		props.put("realm.ldap.userTeamLinkAttributeRegex", "cn=([^,]+),");
		props.put("realm.ldap.userTeamLinkAttributeRegexGroup", "1");
		props.put("realm.ldap.teamUserLinkAttributeName", "member");
		props.put("realm.ldap.teamUserLinkAttributeRegex", "cn=([^,]+),");
		props.put("realm.ldap.teamUserLinkAttributeRegexGroup", "1");
		props.put("realm.ldap.excludeFromFederationAttributeName", "");
		props.put("realm.ldap.excludeFromFederationAttributeValue", "");
		props.put("realm.ldap.preReceiveScriptsAttributeName", "");
		props.put("realm.ldap.postReceiveScriptsAttributeName", "");
		
		// Mock out our settings
		settings = new MemorySettings(props);
		gitBlit.configureContext(settings, false);
		
		// Mock out our LDAP
		ldapUserService = new LdapUserService() {
			@Override
			protected DirContext getLdapDirContext(String principal, String credentials) {
				return getMockDirContext();
			}
		};
		ldapUserService.setup(settings);
	}
	
	@After
	public void teardown() throws Exception {
		File f = GitBlit.getFileOrFolder("ldapUserServiceTest.conf");
		f.delete();
		
		File f2 = GitBlit.getFileOrFolder("ldapUserServiceTest.properties");
		f2.delete();
		
		settings = null;
	}
	
	public StoredConfig getBackingConfiguration() throws IOException, ConfigInvalidException {
		File f = GitBlit.getFileOrFolder("ldapUserServiceTest.conf");
		StoredConfig config = new FileBasedConfig(f, FS.detect());
		config.load();
		
		return config;
	}
	
	private MockDirContext getMockDirContext() {
		MockDirContext answer = new MockDirContext();
		
		// Mock User Search - jcrygier
		Attributes jcrygierSearch = new BasicAttributes();
		jcrygierSearch.put("name", "jcrygier");
		jcrygierSearch.put("excludeFromFederation", "true");
		Attribute jcrygierMemberOfAttribute = new BasicAttribute("memberOf");
		jcrygierSearch.put(jcrygierMemberOfAttribute);
		jcrygierMemberOfAttribute.add("CN=Git_Admins,OU=Security_Groups,OU=User_Control,OU=myOrganization,DC=myCompany");
		jcrygierMemberOfAttribute.add("CN=Git_Users,OU=Security_Groups,OU=User_Control,OU=myOrganization,DC=myCompany");
		SearchResult jcrygierSearchResult = new SearchResult("cn", "jcrygier", jcrygierSearch);
		jcrygierSearchResult.setNameInNamespace("CN=jcrygier,OU=User_Accounts,OU=User_Control,OU=myOrganization,DC=myCompany");
		answer.addSearchResult("OU=User_Accounts,OU=User_Control,OU=myOrganization,DC=myCompany", "(name={0})", new Object[] { "jcrygier" }, jcrygierSearchResult);
		
		// Mock User Search - anotherUser
		Attributes anotherUserSearch = new BasicAttributes();
		anotherUserSearch.put("name", "anotherUser");
		anotherUserSearch.put("excludeFromFederation", "false");
		anotherUserSearch.put("memberOf", "CN=Git_Users,OU=Security_Groups,OU=User_Control,OU=myOrganization,DC=myCompany");
		SearchResult anotherUserSearchResult = new SearchResult("cn", "anotherUser", anotherUserSearch);
		anotherUserSearchResult.setNameInNamespace("CN=anotherUser,OU=User_Accounts,OU=User_Control,OU=myOrganization,DC=myCompany");
		answer.addSearchResult("OU=User_Accounts,OU=User_Control,OU=myOrganization,DC=myCompany", "(name={0})", new Object[] { "anotherUser" }, anotherUserSearchResult);
		
		// All Users Search - re-use above users
		answer.addSearchResult("OU=User_Accounts,OU=User_Control,OU=myOrganization,DC=myCompany", "(objectClass=user)", new Object[] { }, jcrygierSearchResult, anotherUserSearchResult);
		
		// Mock Team Search - Git_Admins 
		Attributes gitAdmins = new BasicAttributes();
		gitAdmins.put("name", "Git_Admins");
		gitAdmins.put("member", "CN=jcrygier,CN=Git_Users,OU=Security_Groups,OU=User_Control,OU=myOrganization,DC=myCompany");
		SearchResult gitAdminsSearchResult = new SearchResult("cn", "Git_Admins", gitAdmins);
		gitAdminsSearchResult.setNameInNamespace("CN=Git_Admins,OU=Security_Groups,OU=User_Control,OU=myOrganization,DC=myCompany");
		answer.addSearchResult("OU=Security_Groups,OU=User_Control,OU=myOrganization,DC=myCompany", "(name={0})", new Object[] { "Git_Admins" }, gitAdminsSearchResult);
		
		// Mock Team Search - Git_Users 
		Attributes gitUsers = new BasicAttributes();
		gitUsers.put("name", "Git_Users");
		gitUsers.put("preReceiveScriptsAttributeName", "jenkins,testscript");
		gitUsers.put("postReceiveScriptsAttributeName", "postReceiveOne,postReceiveOne");
		Attribute gitUsersMemberAttribute = new BasicAttribute("member");
		gitUsers.put(gitUsersMemberAttribute);
		gitUsersMemberAttribute.add("CN=jcrygier,CN=Git_Users,OU=Security_Groups,OU=User_Control,OU=myOrganization,DC=myCompany");
		gitUsersMemberAttribute.add("CN=anotherUser,CN=Git_Users,OU=Security_Groups,OU=User_Control,OU=myOrganization,DC=myCompany");
		SearchResult gitUsersSearchResult = new SearchResult("cn", "Git_Users", gitUsers);
		gitUsersSearchResult.setNameInNamespace("CN=Git_Users,OU=Security_Groups,OU=User_Control,OU=myOrganization,DC=myCompany");
		answer.addSearchResult("OU=Security_Groups,OU=User_Control,OU=myOrganization,DC=myCompany", "(name={0})", new Object[] { "Git_Users" }, gitUsersSearchResult);

		// All Team Search - re-use above teams
		answer.addSearchResult("OU=Security_Groups,OU=User_Control,OU=myOrganization,DC=myCompany", "(&(objectClass=group)(cn=git*))", new Object[] { }, gitAdminsSearchResult, gitUsersSearchResult);
		
		// Users for Git_Admins
		answer.addSearchResult("OU=User_Accounts,OU=User_Control,OU=myOrganization,DC=myCompany", "(memberOf=CN={0},OU=Security_Groups,OU=User_Control,OU=myOrganization,DC=myCompany)", new Object[] { "Git_Admins" }, jcrygierSearchResult);
		
		// Users for Git_Users
		answer.addSearchResult("OU=User_Accounts,OU=User_Control,OU=myOrganization,DC=myCompany", "(memberOf=CN={0},OU=Security_Groups,OU=User_Control,OU=myOrganization,DC=myCompany)", new Object[] { "Git_Users" }, jcrygierSearchResult, anotherUserSearchResult);
		
		// Teams for jcrygier
		answer.addSearchResult("OU=Security_Groups,OU=User_Control,OU=myOrganization,DC=myCompany", "(member={0})", new Object[] { "CN=jcrygier,OU=User_Accounts,OU=User_Control,OU=myOrganization,DC=myCompany" }, gitAdminsSearchResult, gitUsersSearchResult);

		return answer;
	}

	@Test
	public void testAuthenticate() {
		UserModel userModel = ldapUserService.authenticate("domain\\jcrygier", "password".toCharArray());
		
		assertNotNull("UserModel not found", userModel);
		assertEquals("UserModel wrong username", "jcrygier", userModel.getName());
		
		UserModel userModel2 = ldapUserService.authenticate("doesNotExist", "anotherPassword".toCharArray());
		
		assertNull("UserModel found for bogus user", userModel2);
	}
	
	@Test
	public void testGetUserModel() {
		UserModel userModel = ldapUserService.getUserModel("jcrygier");
		
		assertNotNull("UserModel not found", userModel);
		assertEquals("UserModel wrong username", "jcrygier", userModel.getName());
		
		UserModel userModel2 = ldapUserService.getUserModel("anotherUser");
		
		assertNotNull("UserModel not found", userModel2);
		assertEquals("UserModel wrong username", "anotherUser", userModel2.getName());
		
		UserModel userModel3 = ldapUserService.getUserModel("doesNotExist");
		
		assertNull("UserModel incorrectly found", userModel3);
	}
	
	@Test
	public void testGetAllUsers() {
		List<String> allUserNames = ldapUserService.getAllUsernames();
		
		assertNotNull("No Usernames returned", allUserNames);
		assertFalse("No results", allUserNames.isEmpty());
		assertEquals("Number of users wrong", 2, allUserNames.size());
	}
	
	@Test
	public void testGetAllTeams() {
		List<TeamModel> allTeamNames = ldapUserService.getAllTeams();
		
		assertNotNull("No Team Names returned", allTeamNames);
		assertFalse("No resutls", allTeamNames.isEmpty());
		assertEquals("Number of teams wrong", 2, allTeamNames.size());
	}
	
	@Test
	public void testGetAllTeamNames() {
		List<String> allTeamNames = ldapUserService.getAllTeamNames();
		
		assertNotNull("No Team Names returned", allTeamNames);
		assertFalse("No resutls", allTeamNames.isEmpty());
		assertEquals("Number of teams wrong", 2, allTeamNames.size());
		
		assertEquals("Team 1 Wrong Name", "git_admins", allTeamNames.get(0));
		assertEquals("Team 1 Wrong Name", "git_users", allTeamNames.get(1));
	}
	
	@Test
	public void testGetUsersFromTeam() {
		TeamModel team = ldapUserService.getTeamModel("Git_Users");
		
		assertNotNull("No Team returned", team);
		assertEquals("Team Name Wrong", "Git_Users", team.name);
		assertEquals("Team Number of users wrong", 2, team.users.size());
		assertTrue("Team Member Wrong", team.users.contains("jcrygier"));
		assertTrue("Team Member Wrong", team.users.contains("anotheruser"));
	}
	
	@Test
	public void testGetRepositoriesFromTeam() {
		TeamModel team = ldapUserService.getTeamModel("Git_Users");
		
		assertNotNull("No Team returned", team);
		assertEquals("Team Name Wrong", "Git_Users", team.name);
		assertEquals("Team Number of repositories wrong", 3, team.repositories.size());
		assertTrue("Repository Wrong", team.hasRepository("helloworld.git"));
		assertTrue("Repository Wrong", team.hasRepository("repotwo.git"));
		assertTrue("Repository Wrong", team.hasRepository("myrepos/nestedrepo.git"));	
	}
	
	@SuppressWarnings("deprecation")
	@Test
	public void testGetRepositoriesFromUser() {
		UserModel user = ldapUserService.getUserModel("jcrygier");
		
		assertNotNull("No User returned", user);
		assertEquals("User Name Wrong", "jcrygier", user.getName());
		assertTrue("Repository Wrong", user.canAccessRepository("repothree.git"));
		assertTrue("Repository Wrong", user.canAccessRepository("helloworld.git"));
		assertTrue("Repository Wrong", user.canAccessRepository("repotwo.git"));
		assertTrue("Repository Wrong", user.canAccessRepository("myrepos/nestedrepo.git"));		
	}
	
	@Test
	public void testUpdateTeam() throws IOException, ConfigInvalidException {
		TeamModel team = ldapUserService.getTeamModel("Git_Users");
		
		assertNotNull("No Team returned", team);
		assertEquals("Team Name Wrong", "Git_Users", team.name);
		assertEquals("Team Number of repositories wrong", 3, team.repositories.size());
		assertFalse("Repository Wrong", team.hasRepository("testing.git"));
		
		team.addRepository("testing.git");
		ldapUserService.updateTeamModel(team);
		
		StoredConfig config = getBackingConfiguration();
		List<String> repositories = Arrays.asList(config.getStringList("team", "Git_Users", "repository"));
		assertTrue("Repository Wrong", repositories.contains("testing.git"));
		assertEquals("Wrong number of repositories written", 4, repositories.size());
		
		repositories = Arrays.asList(config.getStringList("user", "jcrygier", "repository"));
		assertTrue("Repository Wrong", repositories.contains("repothree.git"));
		assertEquals("Wrong number of repositories written", 1, repositories.size());
		
		assertEquals("AnotherUser is populated", 0, config.getNames("user", "anotherUser").size());
	}
	
	@Test
	public void testUpdateUser() throws IOException, ConfigInvalidException {
		UserModel user = ldapUserService.getUserModel("jcrygier");
		
		assertNotNull("No User returned", user);
		assertEquals("User Name Wrong", "jcrygier", user.getName());
		assertTrue(user.excludeFromFederation);
		
		user.excludeFromFederation = false;
		ldapUserService.updateUserModel(user);
		
		StoredConfig config = getBackingConfiguration();
		List<String> roles = Arrays.asList(config.getStringList("user", "jcrygier", "role"));
		assertFalse("Exclude From Federation role missing Wrong", roles.contains("#notfederated"));
		
		user = ldapUserService.getUserModel("anotherUser");
		
		assertNotNull("No User returned", user);
		assertFalse(user.excludeFromFederation);
	}
	
	@Test
	public void testExcludeFedarationFromLdap() {
		settings.put("realm.ldap.excludeFromFederationAttributeName", "excludeFromFederation");
		settings.put("realm.ldap.excludeFromFederationAttributeValue", "true");
		
		UserModel user = ldapUserService.getUserModel("jcrygier");
		
		assertNotNull("No User returned", user);
		assertTrue(user.excludeFromFederation);
		
		user = ldapUserService.getUserModel("anotherUser");
		
		assertNotNull("No User returned", user);
		assertFalse(user.excludeFromFederation);
	}
	
	@Test
	public void testPreReceiveScriptsFromLdap() {
		settings.put("realm.ldap.preReceiveScriptsAttributeName", "preReceiveScriptsAttributeName");
		
		TeamModel team = ldapUserService.getTeamModel("Git_Users");
		
		assertNotNull(team);
		assertTrue(team.preReceiveScripts.contains("jenkins"));
		assertTrue(team.preReceiveScripts.contains("testscript"));
		
		team = ldapUserService.getTeamModel("Git_Admins");
		
		assertNotNull(team);
		assertTrue(team.preReceiveScripts.isEmpty());
	}
	
	@Test
	public void testPreReceiveScriptsFromConfig() {
		TeamModel team = ldapUserService.getTeamModel("Git_Users");
		
		assertNotNull(team);
		assertTrue(team.preReceiveScripts.contains("jenkins"));
		assertTrue(team.preReceiveScripts.contains("testscript"));
		
		team = ldapUserService.getTeamModel("Git_Admins");
		
		assertNotNull(team);
		assertTrue(team.preReceiveScripts.isEmpty());
	}
	
	@Test
	public void testPostReceiveScriptsFromLdap() {
		settings.put("realm.ldap.postReceiveScriptsAttributeName", "postReceiveScriptsAttributeName");
		
		TeamModel team = ldapUserService.getTeamModel("Git_Users");
		
		assertNotNull(team);
		assertTrue(team.postReceiveScripts.contains("postReceiveOne"));
		assertTrue(team.postReceiveScripts.contains("postReceiveTwo"));
		
		team = ldapUserService.getTeamModel("Git_Admins");
		
		assertNotNull(team);
		assertTrue(team.postReceiveScripts.isEmpty());
	}
	
	@Test
	public void testPostReceiveScriptsFromConfig() {
		TeamModel team = ldapUserService.getTeamModel("Git_Users");
		
		assertNotNull(team);
		assertTrue(team.postReceiveScripts.contains("postReceiveOne"));
		assertTrue(team.postReceiveScripts.contains("postReceiveTwo"));
		
		team = ldapUserService.getTeamModel("Git_Admins");
		
		assertNotNull(team);
		assertTrue(team.postReceiveScripts.isEmpty());
	}
	
	@Test
	public void testPropertiesBacking() {
		settings.put("realm.ldap.alternateConfiguration", "ldapUserServiceTest.properties");
		
		TeamModel team = ldapUserService.getTeamModel("Git_Users");
		
		assertNotNull(team);
		assertTrue(team.postReceiveScripts.contains("postReceiveOne"));
		assertTrue(team.postReceiveScripts.contains("postReceiveTwo"));
		
		team = ldapUserService.getTeamModel("Git_Admins");
		
		assertNotNull(team);
		assertTrue(team.postReceiveScripts.isEmpty());
	}


}
