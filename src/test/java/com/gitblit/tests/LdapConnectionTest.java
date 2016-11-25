package com.gitblit.tests;

import static org.junit.Assume.assumeTrue;

import java.util.ArrayList;
import java.util.Arrays;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.gitblit.Keys;
import com.gitblit.ldap.LdapConnection;
import com.unboundid.ldap.sdk.BindResult;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.ResultCode;
import com.unboundid.ldap.sdk.SearchRequest;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchResultEntry;
import com.unboundid.ldap.sdk.SearchScope;

/*
 * Test for the LdapConnection
 *
 * @author Florian Zschocke
 *
 */
@RunWith(Parameterized.class)
public class LdapConnectionTest extends LdapBasedUnitTest {

	@Test
	public void testEscapeLDAPFilterString() {
		// This test is independent from authentication mode, so run only once.
		assumeTrue(authMode == AuthMode.ANONYMOUS);

		// From: https://www.owasp.org/index.php/Preventing_LDAP_Injection_in_Java
		assertEquals("No special characters to escape", "Hi This is a test #çà", LdapConnection.escapeLDAPSearchFilter("Hi This is a test #çà"));
		assertEquals("LDAP Christams Tree", "Hi \\28This\\29 = is \\2a a \\5c test # ç à ô", LdapConnection.escapeLDAPSearchFilter("Hi (This) = is * a \\ test # ç à ô"));

		assertEquals("Injection", "\\2a\\29\\28userPassword=secret", LdapConnection.escapeLDAPSearchFilter("*)(userPassword=secret"));
	}


	@Test
	public void testConnect() {
		// This test is independent from authentication mode, so run only once.
		assumeTrue(authMode == AuthMode.ANONYMOUS);

		LdapConnection conn = new LdapConnection(settings);
		try {
			assertTrue(conn.connect());
		} finally {
			conn.close();
		}
	}


	@Test
	public void testBindAnonymous() {
		// This test tests for anonymous bind, so run only in authentication mode ANONYMOUS.
		assumeTrue(authMode == AuthMode.ANONYMOUS);

		LdapConnection conn = new LdapConnection(settings);
		try {
			assertTrue(conn.connect());

			BindResult br = conn.bind();
			assertNotNull(br);
			assertEquals(ResultCode.SUCCESS, br.getResultCode());
			assertEquals("", authMode.getBindTracker().getLastSuccessfulBindDN(br.getMessageID()));

		} finally {
			conn.close();
		}
	}


	@Test
	public void testBindAsAdmin() {
		// This test tests for anonymous bind, so run only in authentication mode DS_MANAGER.
		assumeTrue(authMode == AuthMode.DS_MANAGER);

		LdapConnection conn = new LdapConnection(settings);
		try {
			assertTrue(conn.connect());

			BindResult br = conn.bind();
			assertNotNull(br);
			assertEquals(ResultCode.SUCCESS, br.getResultCode());
			assertEquals(settings.getString(Keys.realm.ldap.username, "UNSET"), authMode.getBindTracker().getLastSuccessfulBindDN(br.getMessageID()));

		} finally {
			conn.close();
		}
	}


	@Test
	public void testBindToBindpattern() {
		LdapConnection conn = new LdapConnection(settings);
		try {
			assertTrue(conn.connect());

			String bindPattern = "CN=${username},OU=Canada," + ACCOUNT_BASE;

			BindResult br = conn.bind(bindPattern, "UserThree", "userThreePassword");
			assertNotNull(br);
			assertEquals(ResultCode.SUCCESS, br.getResultCode());
			assertEquals("CN=UserThree,OU=Canada," + ACCOUNT_BASE, authMode.getBindTracker().getLastSuccessfulBindDN(br.getMessageID()));

			br = conn.bind(bindPattern, "UserFour", "userThreePassword");
			assertNull(br);

			br = conn.bind(bindPattern, "UserTwo", "userTwoPassword");
			assertNull(br);

		} finally {
			conn.close();
		}
	}


	@Test
	public void testRebindAsUser() {
		LdapConnection conn = new LdapConnection(settings);
		try {
			assertTrue(conn.connect());

			assertFalse(conn.rebindAsUser());

			BindResult br = conn.bind();
			assertNotNull(br);
			assertFalse(conn.rebindAsUser());


			String bindPattern = "CN=${username},OU=Canada," + ACCOUNT_BASE;
			br = conn.bind(bindPattern, "UserThree", "userThreePassword");
			assertNotNull(br);
			assertFalse(conn.rebindAsUser());

			br = conn.bind();
			assertNotNull(br);
			assertTrue(conn.rebindAsUser());
			assertEquals(ResultCode.SUCCESS, br.getResultCode());
			assertEquals("CN=UserThree,OU=Canada," + ACCOUNT_BASE, authMode.getBindTracker().getLastSuccessfulBindDN());

		} finally {
			conn.close();
		}
	}



	@Test
	public void testSearchRequest() throws LDAPException {
		LdapConnection conn = new LdapConnection(settings);
		try {
			assertTrue(conn.connect());
			BindResult br = conn.bind();
			assertNotNull(br);

			SearchRequest req;
			SearchResult result;
			SearchResultEntry entry;

			req = new SearchRequest(ACCOUNT_BASE, SearchScope.BASE, "(CN=UserOne)");
			result = conn.search(req);
			assertNotNull(result);
			assertEquals(0, result.getEntryCount());

			req = new SearchRequest(ACCOUNT_BASE, SearchScope.ONE, "(CN=UserTwo)");
			result = conn.search(req);
			assertNotNull(result);
			assertEquals(0, result.getEntryCount());

			req = new SearchRequest(ACCOUNT_BASE, SearchScope.SUB, "(CN=UserThree)");
			result = conn.search(req);
			assertNotNull(result);
			assertEquals(1, result.getEntryCount());
			entry = result.getSearchEntries().get(0);
			assertEquals("CN=UserThree,OU=Canada," + ACCOUNT_BASE, entry.getDN());

			req = new SearchRequest(ACCOUNT_BASE, SearchScope.SUBORDINATE_SUBTREE, "(CN=UserFour)");
			result = conn.search(req);
			assertNotNull(result);
			assertEquals(1, result.getEntryCount());
			entry = result.getSearchEntries().get(0);
			assertEquals("CN=UserFour,OU=Canada," + ACCOUNT_BASE, entry.getDN());

		} finally {
			conn.close();
		}
	}


	@Test
	public void testSearch() throws LDAPException {
		LdapConnection conn = new LdapConnection(settings);
		try {
			assertTrue(conn.connect());
			BindResult br = conn.bind();
			assertNotNull(br);

			SearchResult result;
			SearchResultEntry entry;

			result = conn.search(ACCOUNT_BASE, false, "(CN=UserOne)", null);
			assertNotNull(result);
			assertEquals(1, result.getEntryCount());
			entry = result.getSearchEntries().get(0);
			assertEquals("CN=UserOne,OU=US," + ACCOUNT_BASE, entry.getDN());

			result = conn.search(ACCOUNT_BASE, true, "(&(CN=UserOne)(surname=One))", null);
			assertNotNull(result);
			assertEquals(1, result.getEntryCount());
			entry = result.getSearchEntries().get(0);
			assertEquals("CN=UserOne,OU=US," + ACCOUNT_BASE, entry.getDN());

			result = conn.search(ACCOUNT_BASE, true, "(&(CN=UserOne)(surname=Two))", null);
			assertNotNull(result);
			assertEquals(0, result.getEntryCount());

			result = conn.search(ACCOUNT_BASE, true, "(surname=Two)", Arrays.asList("givenName", "surname"));
			assertNotNull(result);
			assertEquals(1, result.getEntryCount());
			entry = result.getSearchEntries().get(0);
			assertEquals("CN=UserTwo,OU=US," + ACCOUNT_BASE, entry.getDN());
			assertEquals(2, entry.getAttributes().size());
			assertEquals("User", entry.getAttributeValue("givenName"));
			assertEquals("Two", entry.getAttributeValue("surname"));

			result = conn.search(ACCOUNT_BASE, true, "(personalTitle=Mr*)", null);
			assertNotNull(result);
			assertEquals(3, result.getEntryCount());
			ArrayList<String> names = new ArrayList<>(3);
			names.add(result.getSearchEntries().get(0).getAttributeValue("surname"));
			names.add(result.getSearchEntries().get(1).getAttributeValue("surname"));
			names.add(result.getSearchEntries().get(2).getAttributeValue("surname"));
			assertTrue(names.contains("One"));
			assertTrue(names.contains("Two"));
			assertTrue(names.contains("Three"));

		} finally {
			conn.close();
		}
	}


	@Test
	public void testSearchUser() throws LDAPException {
		LdapConnection conn = new LdapConnection(settings);
		try {
			assertTrue(conn.connect());
			BindResult br = conn.bind();
			assertNotNull(br);

			SearchResult result;
			SearchResultEntry entry;

			result = conn.searchUser("UserOne");
			assertNotNull(result);
			assertEquals(1, result.getEntryCount());
			entry = result.getSearchEntries().get(0);
			assertEquals("CN=UserOne,OU=US," + ACCOUNT_BASE, entry.getDN());

			result = conn.searchUser("UserFour", Arrays.asList("givenName", "surname"));
			assertNotNull(result);
			assertEquals(1, result.getEntryCount());
			entry = result.getSearchEntries().get(0);
			assertEquals("CN=UserFour,OU=Canada," + ACCOUNT_BASE, entry.getDN());
			assertEquals(2, entry.getAttributes().size());
			assertEquals("User", entry.getAttributeValue("givenName"));
			assertEquals("Four", entry.getAttributeValue("surname"));

		} finally {
			conn.close();
		}
	}

}
