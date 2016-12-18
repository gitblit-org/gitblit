package com.gitblit.tests;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.gitblit.Keys;
import com.gitblit.tests.mock.MemorySettings;
import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.listener.InMemoryDirectoryServerSnapshot;
import com.unboundid.ldap.listener.InMemoryListenerConfig;
import com.unboundid.ldap.listener.interceptor.InMemoryInterceptedRequest;
import com.unboundid.ldap.listener.interceptor.InMemoryInterceptedResult;
import com.unboundid.ldap.listener.interceptor.InMemoryInterceptedSearchEntry;
import com.unboundid.ldap.listener.interceptor.InMemoryInterceptedSearchRequest;
import com.unboundid.ldap.listener.interceptor.InMemoryInterceptedSearchResult;
import com.unboundid.ldap.listener.interceptor.InMemoryInterceptedSimpleBindResult;
import com.unboundid.ldap.listener.interceptor.InMemoryOperationInterceptor;
import com.unboundid.ldap.sdk.BindRequest;
import com.unboundid.ldap.sdk.BindResult;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.LDAPResult;
import com.unboundid.ldap.sdk.OperationType;
import com.unboundid.ldap.sdk.ResultCode;
import com.unboundid.ldap.sdk.SimpleBindRequest;



/**
 * Base class for Unit (/Integration) tests that test going against an
 * in-memory UnboundID LDAP server.
 *
 * This base class creates separate in-memory LDAP servers for different scenarios:
 * - ANONYMOUS: anonymous bind to LDAP.
 * - DS_MANAGER: The DIRECTORY_MANAGER is set as DN to bind as an admin.
 *               Normal users are prohibited to search the DS, they can only bind.
 * - USR_MANAGER: The USER_MANAGER is set as DN to bind as an admin.
 *                This account can only search users but not groups. Normal users can search groups.
 *
 * @author Florian Zschocke
 *
 */
public abstract class LdapBasedUnitTest extends GitblitUnitTest {

	protected static final String RESOURCE_DIR = "src/test/resources/ldap/";
	private static final String DIRECTORY_MANAGER = "cn=Directory Manager";
	private static final String USER_MANAGER = "cn=UserManager";
	protected static final String ACCOUNT_BASE = "OU=Users,OU=UserControl,OU=MyOrganization,DC=MyDomain";
	private static final String GROUP_BASE = "OU=Groups,OU=UserControl,OU=MyOrganization,DC=MyDomain";
	protected static final String DN_USER_ONE = "CN=UserOne,OU=US," + ACCOUNT_BASE;
	protected static final String DN_USER_TWO = "CN=UserTwo,OU=US," + ACCOUNT_BASE;
	protected static final String DN_USER_THREE = "CN=UserThree,OU=Canada," + ACCOUNT_BASE;


	/**
	 * Enumeration of different test modes, representing different use scenarios.
	 * With ANONYMOUS anonymous binds are used to search LDAP.
	 * DS_MANAGER will use a DIRECTORY_MANAGER to search LDAP. Normal users are prohibited to search the DS.
	 * With USR_MANAGER, a USER_MANAGER account is used to search in LDAP. This account can only search users
	 * but not groups. Normal users can search groups, though.
	 *
	 */
	protected enum AuthMode {
		ANONYMOUS,
		DS_MANAGER,
		USR_MANAGER;


		private int ldapPort;
		private InMemoryDirectoryServer ds;
		private InMemoryDirectoryServerSnapshot dsSnapshot;
		private BindTracker bindTracker;

		void setLdapPort(int port) {
			this.ldapPort = port;
		}

		int ldapPort() {
			return this.ldapPort;
		}

		void setDS(InMemoryDirectoryServer ds) {
			if (this.ds == null) {
				this.ds = ds;
				this.dsSnapshot = ds.createSnapshot();
			};
		}

		InMemoryDirectoryServer getDS() {
			return ds;
		}

		void setBindTracker(BindTracker bindTracker) {
			this.bindTracker = bindTracker;
		}

		BindTracker getBindTracker() {
			return bindTracker;
		}

		void restoreSnapshot() {
			ds.restoreSnapshot(dsSnapshot);
		}
	}

	@Parameter
	public AuthMode authMode = AuthMode.ANONYMOUS;

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();


	protected File usersConf;

	protected MemorySettings settings;


	/**
	 * Run the tests with each authentication scenario once.
	 */
	@Parameters(name = "{0}")
	public static Collection<Object[]> data() {
		return Arrays.asList(new Object[][] { {AuthMode.ANONYMOUS}, {AuthMode.DS_MANAGER}, {AuthMode.USR_MANAGER} });
	}


	/**
	 * Create three different in memory DS.
	 *
	 * Each DS has a different configuration:
	 * The first allows anonymous binds.
	 * The second requires authentication for all operations. It will only allow the DIRECTORY_MANAGER account
	 * to search for users and groups.
	 * The third one is like the second, but it allows users to search for users and groups, and restricts the
	 * USER_MANAGER from searching for groups.
	 */
	@BeforeClass
	public static void ldapInit() throws Exception {
		InMemoryDirectoryServer ds;
		InMemoryDirectoryServerConfig config = createInMemoryLdapServerConfig(AuthMode.ANONYMOUS);
		config.setListenerConfigs(InMemoryListenerConfig.createLDAPConfig("anonymous"));
		ds = createInMemoryLdapServer(config);
		AuthMode.ANONYMOUS.setDS(ds);
		AuthMode.ANONYMOUS.setLdapPort(ds.getListenPort("anonymous"));


		config = createInMemoryLdapServerConfig(AuthMode.DS_MANAGER);
		config.setListenerConfigs(InMemoryListenerConfig.createLDAPConfig("ds_manager"));
		config.setAuthenticationRequiredOperationTypes(EnumSet.allOf(OperationType.class));
		ds = createInMemoryLdapServer(config);
		AuthMode.DS_MANAGER.setDS(ds);
		AuthMode.DS_MANAGER.setLdapPort(ds.getListenPort("ds_manager"));


		config = createInMemoryLdapServerConfig(AuthMode.USR_MANAGER);
		config.setListenerConfigs(InMemoryListenerConfig.createLDAPConfig("usr_manager"));
		config.setAuthenticationRequiredOperationTypes(EnumSet.allOf(OperationType.class));
		ds = createInMemoryLdapServer(config);
		AuthMode.USR_MANAGER.setDS(ds);
		AuthMode.USR_MANAGER.setLdapPort(ds.getListenPort("usr_manager"));

	}

	@AfterClass
	public static void destroy() throws Exception {
		for (AuthMode am : AuthMode.values()) {
			am.getDS().shutDown(true);
		}
	}

	public static InMemoryDirectoryServer createInMemoryLdapServer(InMemoryDirectoryServerConfig config) throws Exception {
		InMemoryDirectoryServer imds = new InMemoryDirectoryServer(config);
		imds.importFromLDIF(true, RESOURCE_DIR + "sampledata.ldif");
		imds.startListening();
		return imds;
	}

	public static InMemoryDirectoryServerConfig createInMemoryLdapServerConfig(AuthMode authMode) throws Exception {
		InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig("dc=MyDomain");
		config.addAdditionalBindCredentials(DIRECTORY_MANAGER, "password");
		config.addAdditionalBindCredentials(USER_MANAGER, "passwd");
		config.setSchema(null);

		authMode.setBindTracker(new BindTracker());
		config.addInMemoryOperationInterceptor(authMode.getBindTracker());
		config.addInMemoryOperationInterceptor(new AccessInterceptor(authMode));

		return config;
	}



	@Before
	public void setupBase() throws Exception {
		authMode.restoreSnapshot();
		authMode.getBindTracker().reset();

		usersConf = folder.newFile("users.conf");
		FileUtils.copyFile(new File(RESOURCE_DIR + "users.conf"), usersConf);
		settings = getSettings();
	}


	protected InMemoryDirectoryServer getDS() {
		return authMode.getDS();
	}



	protected MemorySettings getSettings() {
		Map<String, Object> backingMap = new HashMap<String, Object>();
		backingMap.put(Keys.realm.userService, usersConf.getAbsolutePath());
		backingMap.put(Keys.realm.ldap.server, "ldap://localhost:" + authMode.ldapPort());
		switch(authMode) {
		case ANONYMOUS:
			backingMap.put(Keys.realm.ldap.username, "");
			backingMap.put(Keys.realm.ldap.password, "");
			break;
		case DS_MANAGER:
			backingMap.put(Keys.realm.ldap.username, DIRECTORY_MANAGER);
			backingMap.put(Keys.realm.ldap.password, "password");
			break;
		case USR_MANAGER:
			backingMap.put(Keys.realm.ldap.username, USER_MANAGER);
			backingMap.put(Keys.realm.ldap.password, "passwd");
			break;
		default:
			throw new RuntimeException("Unimplemented AuthMode case!");

		}
		backingMap.put(Keys.realm.ldap.maintainTeams, "true");
		backingMap.put(Keys.realm.ldap.accountBase, ACCOUNT_BASE);
		backingMap.put(Keys.realm.ldap.accountPattern, "(&(objectClass=person)(sAMAccountName=${username}))");
		backingMap.put(Keys.realm.ldap.groupBase, GROUP_BASE);
		backingMap.put(Keys.realm.ldap.groupMemberPattern, "(&(objectClass=group)(member=${dn}))");
		backingMap.put(Keys.realm.ldap.admins, "UserThree @Git_Admins \"@Git Admins\"");
		backingMap.put(Keys.realm.ldap.displayName, "displayName");
		backingMap.put(Keys.realm.ldap.email, "email");
		backingMap.put(Keys.realm.ldap.uid, "sAMAccountName");

		MemorySettings ms = new MemorySettings(backingMap);
		return ms;
	}




	/**
	 * Operation interceptor for the in memory DS. This interceptor
	 * tracks bind requests.
	 *
	 */
	protected static class BindTracker extends InMemoryOperationInterceptor {
		private Map<Integer,String> lastSuccessfulBindDNs = new HashMap<>();
		private String lastSuccessfulBindDN;


		@Override
		public void processSimpleBindResult(InMemoryInterceptedSimpleBindResult bind) {
			BindResult result = bind.getResult();
			if (result.getResultCode() == ResultCode.SUCCESS) {
				 BindRequest bindRequest = bind.getRequest();
				 lastSuccessfulBindDNs.put(bind.getMessageID(), ((SimpleBindRequest)bindRequest).getBindDN());
				 lastSuccessfulBindDN = ((SimpleBindRequest)bindRequest).getBindDN();
			}
		}

		String getLastSuccessfulBindDN() {
			return lastSuccessfulBindDN;
		}

		String getLastSuccessfulBindDN(int messageID) {
			return lastSuccessfulBindDNs.get(messageID);
		}

		void reset() {
			lastSuccessfulBindDNs = new HashMap<>();
			lastSuccessfulBindDN = null;
		}
	}



	/**
	 * Operation interceptor for the in memory DS. This interceptor
	 * implements access restrictions for certain user/DN combinations.
	 *
	 * The USER_MANAGER is only allowed to search for users, but not for groups.
	 * This is to test the original behaviour where the teams were searched under
	 * the user binding.
	 * When running in a DIRECTORY_MANAGER scenario, only the manager account
	 * is allowed to search for users and groups, while a normal user may not do so.
	 * This tests the scenario where a normal user cannot read teams and thus the
	 * manager account needs to be used for all searches.
	 *
	 */
	protected static class AccessInterceptor extends InMemoryOperationInterceptor {
		AuthMode authMode;
		Map<Long,String> lastSuccessfulBindDN = new HashMap<>();
		Map<Long,Boolean> resultProhibited = new HashMap<>();

		public AccessInterceptor(AuthMode authMode) {
			this.authMode = authMode;
		}


		@Override
		public void processSimpleBindResult(InMemoryInterceptedSimpleBindResult bind) {
			BindResult result = bind.getResult();
			if (result.getResultCode() == ResultCode.SUCCESS) {
				 BindRequest bindRequest = bind.getRequest();
				 lastSuccessfulBindDN.put(bind.getConnectionID(), ((SimpleBindRequest)bindRequest).getBindDN());
				 resultProhibited.remove(bind.getConnectionID());
			}
		}



		@Override
		public void processSearchRequest(InMemoryInterceptedSearchRequest request) throws LDAPException {
			String bindDN = getLastBindDN(request);

			if (USER_MANAGER.equals(bindDN)) {
				if (request.getRequest().getBaseDN().endsWith(GROUP_BASE)) {
					throw new LDAPException(ResultCode.NO_SUCH_OBJECT);
				}
			}
			else if(authMode == AuthMode.DS_MANAGER && !DIRECTORY_MANAGER.equals(bindDN)) {
				throw new LDAPException(ResultCode.NO_SUCH_OBJECT);
			}
		}


		@Override
		public void processSearchEntry(InMemoryInterceptedSearchEntry entry) {
			String bindDN = getLastBindDN(entry);

			boolean prohibited = false;

			if (USER_MANAGER.equals(bindDN)) {
				if (entry.getSearchEntry().getDN().endsWith(GROUP_BASE)) {
					prohibited = true;
				}
			}
			else if(authMode == AuthMode.DS_MANAGER && !DIRECTORY_MANAGER.equals(bindDN)) {
				prohibited = true;
			}

			if (prohibited) {
				// Found entry prohibited for bound user. Setting entry to null.
				entry.setSearchEntry(null);
				resultProhibited.put(entry.getConnectionID(), Boolean.TRUE);
			}
		}

		@Override
		public void processSearchResult(InMemoryInterceptedSearchResult result) {
			String bindDN = getLastBindDN(result);

			boolean prohibited = false;

			Boolean rspb = resultProhibited.get(result.getConnectionID());
			if (USER_MANAGER.equals(bindDN)) {
				if (rspb != null && rspb) {
					prohibited = true;
				}
			}
			else if(authMode == AuthMode.DS_MANAGER && !DIRECTORY_MANAGER.equals(bindDN)) {
				if (rspb != null && rspb) {
					prohibited = true;
				}
			}

			if (prohibited) {
				// Result prohibited for bound user. Returning error
				result.setResult(new LDAPResult(result.getMessageID(), ResultCode.INSUFFICIENT_ACCESS_RIGHTS));
				resultProhibited.remove(result.getConnectionID());
			}
		}

		private String getLastBindDN(InMemoryInterceptedResult result) {
			String bindDN = lastSuccessfulBindDN.get(result.getConnectionID());
			if (bindDN == null) {
				return "UNKNOWN";
			}
			return bindDN;
		}
		private String getLastBindDN(InMemoryInterceptedRequest request) {
			String bindDN = lastSuccessfulBindDN.get(request.getConnectionID());
			if (bindDN == null) {
				return "UNKNOWN";
			}
			return bindDN;
		}
	}

}
