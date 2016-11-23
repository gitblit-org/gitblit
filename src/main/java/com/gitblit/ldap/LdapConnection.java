package com.gitblit.ldap;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.utils.StringUtils;
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
import com.unboundid.ldap.sdk.SearchScope;
import com.unboundid.ldap.sdk.SimpleBindRequest;
import com.unboundid.ldap.sdk.extensions.StartTLSExtendedRequest;
import com.unboundid.util.ssl.SSLUtil;
import com.unboundid.util.ssl.TrustAllTrustManager;

public class LdapConnection implements AutoCloseable {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private IStoredSettings settings;

	private LDAPConnection conn;
	private SimpleBindRequest currentBindRequest;
	private SimpleBindRequest managerBindRequest;
	private SimpleBindRequest userBindRequest;


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



	public LdapConnection(IStoredSettings settings) {
		this.settings = settings;

		String bindUserName = settings.getString(Keys.realm.ldap.username, "");
		String bindPassword = settings.getString(Keys.realm.ldap.password, "");
		if (StringUtils.isEmpty(bindUserName) && StringUtils.isEmpty(bindPassword)) {
			this.managerBindRequest = new SimpleBindRequest();
		}
		this.managerBindRequest = new SimpleBindRequest(bindUserName, bindPassword);
	}



	public boolean connect() {
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


	public void close() {
		if (conn != null) {
			conn.close();
		}
	}



	/**
	 * Bind using the manager credentials set in realm.ldap.username and ..password
	 * @return A bind result, or null if binding failed.
	 */
	public BindResult bind() {
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
	public BindResult bind(String bindPattern, String simpleUsername, String password) {
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


	public boolean rebindAsUser() {
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



	public SearchResult search(SearchRequest request) {
		try {
			return conn.search(request);
		} catch (LDAPSearchException e) {
			logger.error("Problem Searching LDAP [{}]",  e.getResultCode());
			return e.getSearchResult();
		}
	}


	public SearchResult search(String base, boolean dereferenceAliases, String filter, List<String> attributes) {
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



	public boolean isAuthenticated(String userDn, String password) {
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
