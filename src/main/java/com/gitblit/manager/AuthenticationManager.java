/*
 * Copyright 2013 gitblit.com.
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
package com.gitblit.manager;

import java.nio.charset.Charset;
import java.security.Principal;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.wicket.RequestCycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.Constants;
import com.gitblit.Constants.AccountType;
import com.gitblit.Constants.AuthenticationType;
import com.gitblit.Constants.Role;
import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.auth.AuthenticationProvider;
import com.gitblit.auth.AuthenticationProvider.UsernamePasswordAuthenticationProvider;
import com.gitblit.auth.HtpasswdAuthProvider;
import com.gitblit.auth.LdapAuthProvider;
import com.gitblit.auth.PAMAuthProvider;
import com.gitblit.auth.RedmineAuthProvider;
import com.gitblit.auth.SalesforceAuthProvider;
import com.gitblit.auth.WindowsAuthProvider;
import com.gitblit.models.TeamModel;
import com.gitblit.models.UserModel;
import com.gitblit.transport.ssh.SshKey;
import com.gitblit.utils.Base64;
import com.gitblit.utils.HttpUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.utils.X509Utils.X509Metadata;
import com.gitblit.wicket.GitBlitWebSession;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * The authentication manager handles user login & logout.
 *
 * @author James Moger
 *
 */
@Singleton
public class AuthenticationManager implements IAuthenticationManager {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final IStoredSettings settings;

	private final IRuntimeManager runtimeManager;

	private final IUserManager userManager;

	private final List<AuthenticationProvider> authenticationProviders;

	private final Map<String, Class<? extends AuthenticationProvider>> providerNames;

	private final Map<String, String> legacyRedirects;

	@Inject
	public AuthenticationManager(
			IRuntimeManager runtimeManager,
			IUserManager userManager) {

		this.settings = runtimeManager.getSettings();
		this.runtimeManager = runtimeManager;
		this.userManager = userManager;
		this.authenticationProviders = new ArrayList<AuthenticationProvider>();

		// map of shortcut provider names
		providerNames = new HashMap<String, Class<? extends AuthenticationProvider>>();
		providerNames.put("htpasswd", HtpasswdAuthProvider.class);
		providerNames.put("ldap", LdapAuthProvider.class);
		providerNames.put("pam", PAMAuthProvider.class);
		providerNames.put("redmine", RedmineAuthProvider.class);
		providerNames.put("salesforce", SalesforceAuthProvider.class);
		providerNames.put("windows", WindowsAuthProvider.class);

		// map of legacy external user services
		legacyRedirects = new HashMap<String, String>();
		legacyRedirects.put("com.gitblit.HtpasswdUserService", "htpasswd");
		legacyRedirects.put("com.gitblit.LdapUserService", "ldap");
		legacyRedirects.put("com.gitblit.PAMUserService", "pam");
		legacyRedirects.put("com.gitblit.RedmineUserService", "redmine");
		legacyRedirects.put("com.gitblit.SalesforceUserService", "salesforce");
		legacyRedirects.put("com.gitblit.WindowsUserService", "windows");
	}

	@Override
	public AuthenticationManager start() {
		// automatically adjust legacy configurations
		String realm = settings.getString(Keys.realm.userService, "${baseFolder}/users.conf");
		if (legacyRedirects.containsKey(realm)) {
			logger.warn("");
			logger.warn(Constants.BORDER2);
			logger.warn(" IUserService '{}' is obsolete!", realm);
			logger.warn(" Please set '{}={}'", "realm.authenticationProviders", legacyRedirects.get(realm));
			logger.warn(Constants.BORDER2);
			logger.warn("");

			// conditionally override specified authentication providers
			if (StringUtils.isEmpty(settings.getString(Keys.realm.authenticationProviders, null))) {
				settings.overrideSetting(Keys.realm.authenticationProviders, legacyRedirects.get(realm));
			}
		}

		// instantiate and setup specified authentication providers
		List<String> providers = settings.getStrings(Keys.realm.authenticationProviders);
		if (providers.isEmpty()) {
			logger.info("External authentication disabled.");
		} else {
			for (String provider : providers) {
				try {
					Class<?> authClass;
					if (providerNames.containsKey(provider)) {
						// map the name -> class
						authClass = providerNames.get(provider);
					} else {
						// reflective lookup
						authClass = Class.forName(provider);
					}
					logger.info("setting up {}", authClass.getName());
					AuthenticationProvider authImpl = (AuthenticationProvider) authClass.newInstance();
					authImpl.setup(runtimeManager, userManager);
					authenticationProviders.add(authImpl);
				} catch (Exception e) {
					logger.error("", e);
				}
			}
		}
		return this;
	}

	@Override
	public AuthenticationManager stop() {
		for (AuthenticationProvider provider : authenticationProviders) {
			try {
				provider.stop();
			} catch (Exception e) {
				logger.error("Failed to stop " + provider.getClass().getSimpleName(), e);
			}
		}
		return this;
	}

	public void addAuthenticationProvider(AuthenticationProvider prov) {
		authenticationProviders.add(prov);
	}

	/**
	 * Authenticate a user based on HTTP request parameters.
	 *
	 * Authentication by X509Certificate is tried first and then by cookie.
	 *
	 * @param httpRequest
	 * @return a user object or null
	 */
	@Override
	public UserModel authenticate(HttpServletRequest httpRequest) {
		return authenticate(httpRequest, false);
	}

	/**
	 * Authenticate a user based on HTTP request parameters.
	 *
	 * Authentication by servlet container principal, X509Certificate, cookie,
	 * and finally BASIC header.
	 *
	 * @param httpRequest
	 * @param requiresCertificate
	 * @return a user object or null
	 */
	@Override
	public UserModel authenticate(HttpServletRequest httpRequest, boolean requiresCertificate) {
		// try to authenticate by servlet container principal
		if (!requiresCertificate) {
			Principal principal = httpRequest.getUserPrincipal();
			if (principal != null) {
				String username = principal.getName();
				if (!StringUtils.isEmpty(username)) {
					boolean internalAccount = userManager.isInternalAccount(username);
					UserModel user = userManager.getUserModel(username);
					if (user != null) {
						// existing user
						flagWicketSession(AuthenticationType.CONTAINER);
						logger.debug(MessageFormat.format("{0} authenticated by servlet container principal from {1}",
								user.username, httpRequest.getRemoteAddr()));
						return validateAuthentication(user, AuthenticationType.CONTAINER);
					} else if (settings.getBoolean(Keys.realm.container.autoCreateAccounts, false)
							&& !internalAccount) {
						// auto-create user from an authenticated container principal
						user = new UserModel(username.toLowerCase());
						user.displayName = username;
						user.password = Constants.EXTERNAL_ACCOUNT;
						user.accountType = AccountType.CONTAINER;
						userManager.updateUserModel(user);
						flagWicketSession(AuthenticationType.CONTAINER);
						logger.debug(MessageFormat.format("{0} authenticated and created by servlet container principal from {1}",
								user.username, httpRequest.getRemoteAddr()));
						return validateAuthentication(user, AuthenticationType.CONTAINER);
					} else if (!internalAccount) {
						logger.warn(MessageFormat.format("Failed to find UserModel for {0}, attempted servlet container authentication from {1}",
								principal.getName(), httpRequest.getRemoteAddr()));
					}
				}
			}
		}

		// try to authenticate by certificate
		boolean checkValidity = settings.getBoolean(Keys.git.enforceCertificateValidity, true);
		String [] oids = settings.getStrings(Keys.git.certificateUsernameOIDs).toArray(new String[0]);
		UserModel model = HttpUtils.getUserModelFromCertificate(httpRequest, checkValidity, oids);
		if (model != null) {
			// grab real user model and preserve certificate serial number
			UserModel user = userManager.getUserModel(model.username);
			X509Metadata metadata = HttpUtils.getCertificateMetadata(httpRequest);
			if (user != null) {
				flagWicketSession(AuthenticationType.CERTIFICATE);
				logger.debug(MessageFormat.format("{0} authenticated by client certificate {1} from {2}",
						user.username, metadata.serialNumber, httpRequest.getRemoteAddr()));
				return validateAuthentication(user, AuthenticationType.CERTIFICATE);
			} else {
				logger.warn(MessageFormat.format("Failed to find UserModel for {0}, attempted client certificate ({1}) authentication from {2}",
						model.username, metadata.serialNumber, httpRequest.getRemoteAddr()));
			}
		}

		if (requiresCertificate) {
			// caller requires client certificate authentication (e.g. git servlet)
			return null;
		}

		UserModel user = null;

		// try to authenticate by cookie
		String cookie = getCookie(httpRequest);
		if (!StringUtils.isEmpty(cookie)) {
			user = userManager.getUserModel(cookie.toCharArray());
			if (user != null) {
				flagWicketSession(AuthenticationType.COOKIE);
				logger.debug(MessageFormat.format("{0} authenticated by cookie from {1}",
					user.username, httpRequest.getRemoteAddr()));
				return validateAuthentication(user, AuthenticationType.COOKIE);
			}
		}

		// try to authenticate by BASIC
		final String authorization = httpRequest.getHeader("Authorization");
		if (authorization != null && authorization.startsWith("Basic")) {
			// Authorization: Basic base64credentials
			String base64Credentials = authorization.substring("Basic".length()).trim();
			String credentials = new String(Base64.decode(base64Credentials),
					Charset.forName("UTF-8"));
			// credentials = username:password
			final String[] values = credentials.split(":", 2);

			if (values.length == 2) {
				String username = values[0];
				char[] password = values[1].toCharArray();
				user = authenticate(username, password);
				if (user != null) {
					flagWicketSession(AuthenticationType.CREDENTIALS);
					logger.debug(MessageFormat.format("{0} authenticated by BASIC request header from {1}",
							user.username, httpRequest.getRemoteAddr()));
					return validateAuthentication(user, AuthenticationType.CREDENTIALS);
				} else {
					logger.warn(MessageFormat.format("Failed login attempt for {0}, invalid credentials from {1}",
							username, httpRequest.getRemoteAddr()));
				}
			}
		}
		return null;
	}

	/**
	 * Authenticate a user based on a public key.
	 *
	 * This implementation assumes that the authentication has already take place
	 * (e.g. SSHDaemon) and that this is a validation/verification of the user.
	 *
	 * @param username
	 * @param key
	 * @return a user object or null
	 */
	@Override
	public UserModel authenticate(String username, SshKey key) {
		if (username != null) {
			if (!StringUtils.isEmpty(username)) {
				UserModel user = userManager.getUserModel(username);
				if (user != null) {
					// existing user
					logger.debug(MessageFormat.format("{0} authenticated by {1} public key",
							user.username, key.getAlgorithm()));
					return validateAuthentication(user, AuthenticationType.PUBLIC_KEY);
				}
				logger.warn(MessageFormat.format("Failed to find UserModel for {0} during public key authentication",
							username));
			}
		} else {
			logger.warn("Empty user passed to AuthenticationManager.authenticate!");
		}
		return null;
	}


	/**
	 * This method allows the authentication manager to reject authentication
	 * attempts.  It is called after the username/secret have been verified to
	 * ensure that the authentication technique has been logged.
	 *
	 * @param user
	 * @return
	 */
	protected UserModel validateAuthentication(UserModel user, AuthenticationType type) {
		if (user == null) {
			return null;
		}
		if (user.disabled) {
			// user has been disabled
			logger.warn("Rejected {} authentication attempt by disabled account \"{}\"",
					type, user.username);
			return null;
		}
		return user;
	}

	protected void flagWicketSession(AuthenticationType authenticationType) {
		RequestCycle requestCycle = RequestCycle.get();
		if (requestCycle != null) {
			// flag the Wicket session, if this is a Wicket request
			GitBlitWebSession session = GitBlitWebSession.get();
			session.authenticationType = authenticationType;
		}
	}

	/**
	 * Authenticate a user based on a username and password.
	 *
	 * @see IUserService.authenticate(String, char[])
	 * @param username
	 * @param password
	 * @return a user object or null
	 */
	@Override
	public UserModel authenticate(String username, char[] password) {
		if (StringUtils.isEmpty(username)) {
			// can not authenticate empty username
			return null;
		}

		String usernameDecoded = StringUtils.decodeUsername(username);
		String pw = new String(password);
		if (StringUtils.isEmpty(pw)) {
			// can not authenticate empty password
			return null;
		}

		UserModel user = userManager.getUserModel(usernameDecoded);

		// try local authentication
		if (user != null && user.isLocalAccount()) {
			return authenticateLocal(user, password);
		}

		// try registered external authentication providers
		for (AuthenticationProvider provider : authenticationProviders) {
			if (provider instanceof UsernamePasswordAuthenticationProvider) {
				UserModel returnedUser = provider.authenticate(usernameDecoded, password);
				if (returnedUser != null) {
					// user authenticated
					returnedUser.accountType = provider.getAccountType();
					return validateAuthentication(returnedUser, AuthenticationType.CREDENTIALS);
				}
			}
		}

		// could not authenticate locally or with a provider
		return null;
	}

	/**
	 * Returns a UserModel if local authentication succeeds.
	 *
	 * @param user
	 * @param password
	 * @return a UserModel if local authentication succeeds, null otherwise
	 */
	protected UserModel authenticateLocal(UserModel user, char [] password) {
		UserModel returnedUser = null;
		if (user.password.startsWith(StringUtils.MD5_TYPE)) {
			// password digest
			String md5 = StringUtils.MD5_TYPE + StringUtils.getMD5(new String(password));
			if (user.password.equalsIgnoreCase(md5)) {
				returnedUser = user;
			}
		} else if (user.password.startsWith(StringUtils.COMBINED_MD5_TYPE)) {
			// username+password digest
			String md5 = StringUtils.COMBINED_MD5_TYPE
					+ StringUtils.getMD5(user.username.toLowerCase() + new String(password));
			if (user.password.equalsIgnoreCase(md5)) {
				returnedUser = user;
			}
		} else if (user.password.equals(new String(password))) {
			// plain-text password
			returnedUser = user;
		}
		return validateAuthentication(returnedUser, AuthenticationType.CREDENTIALS);
	}

	/**
	 * Returns the Gitlbit cookie in the request.
	 *
	 * @param request
	 * @return the Gitblit cookie for the request or null if not found
	 */
	@Override
	public String getCookie(HttpServletRequest request) {
		if (settings.getBoolean(Keys.web.allowCookieAuthentication, true)) {
			Cookie[] cookies = request.getCookies();
			if (cookies != null && cookies.length > 0) {
				for (Cookie cookie : cookies) {
					if (cookie.getName().equals(Constants.NAME)) {
						String value = cookie.getValue();
						return value;
					}
				}
			}
		}
		return null;
	}

	/**
	 * Sets a cookie for the specified user.
	 *
	 * @param response
	 * @param user
	 */
	@Override
	@Deprecated
	public void setCookie(HttpServletResponse response, UserModel user) {
		setCookie(null, response, user);
	}

	/**
	 * Sets a cookie for the specified user.
	 *
	 * @param request
	 * @param response
	 * @param user
	 */
	@Override
	public void setCookie(HttpServletRequest request, HttpServletResponse response, UserModel user) {
		if (settings.getBoolean(Keys.web.allowCookieAuthentication, true)) {
			GitBlitWebSession session = GitBlitWebSession.get();
			boolean standardLogin = session.authenticationType.isStandard();

			if (standardLogin) {
				Cookie userCookie;
				if (user == null) {
					// clear cookie for logout
					userCookie = new Cookie(Constants.NAME, "");
				} else {
					// set cookie for login
					String cookie = userManager.getCookie(user);
					if (StringUtils.isEmpty(cookie)) {
						// create empty cookie
						userCookie = new Cookie(Constants.NAME, "");
					} else {
						// create real cookie
						userCookie = new Cookie(Constants.NAME, cookie);
						// expire the cookie in 7 days
						userCookie.setMaxAge((int) TimeUnit.DAYS.toSeconds(7));
					}
				}
				String path = "/";
				if (request != null) {
					if (!StringUtils.isEmpty(request.getContextPath())) {
						path = request.getContextPath();
					}
				}
				userCookie.setPath(path);
				response.addCookie(userCookie);
			}
		}
	}

	/**
	 * Logout a user.
	 *
	 * @param response
	 * @param user
	 */
	@Override
	@Deprecated
	public void logout(HttpServletResponse response, UserModel user) {
		setCookie(null, response,  null);
	}

	/**
	 * Logout a user.
	 *
	 * @param request
	 * @param response
	 * @param user
	 */
	@Override
	public void logout(HttpServletRequest request, HttpServletResponse response, UserModel user) {
		setCookie(request, response,  null);
	}

	/**
	 * Returns true if the user's credentials can be changed.
	 *
	 * @param user
	 * @return true if the user service supports credential changes
	 */
	@Override
	public boolean supportsCredentialChanges(UserModel user) {
		return (user != null && user.isLocalAccount()) || findProvider(user).supportsCredentialChanges();
	}

	/**
	 * Returns true if the user's display name can be changed.
	 *
	 * @param user
	 * @return true if the user service supports display name changes
	 */
	@Override
	public boolean supportsDisplayNameChanges(UserModel user) {
		return (user != null && user.isLocalAccount()) || findProvider(user).supportsDisplayNameChanges();
	}

	/**
	 * Returns true if the user's email address can be changed.
	 *
	 * @param user
	 * @return true if the user service supports email address changes
	 */
	@Override
	public boolean supportsEmailAddressChanges(UserModel user) {
		return (user != null && user.isLocalAccount()) || findProvider(user).supportsEmailAddressChanges();
	}

	/**
	 * Returns true if the user's team memberships can be changed.
	 *
	 * @param user
	 * @return true if the user service supports team membership changes
	 */
	@Override
	public boolean supportsTeamMembershipChanges(UserModel user) {
		return (user != null && user.isLocalAccount()) || findProvider(user).supportsTeamMembershipChanges();
	}

	/**
	 * Returns true if the team memberships can be changed.
	 *
	 * @param user
	 * @return true if the team membership can be changed
	 */
	@Override
	public boolean supportsTeamMembershipChanges(TeamModel team) {
		return (team != null && team.isLocalTeam()) || findProvider(team).supportsTeamMembershipChanges();
	}

	/**
	 * Returns true if the user's role can be changed.
	 *
	 * @param user
	 * @return true if the user's role can be changed
	 */
	@Override
	public boolean supportsRoleChanges(UserModel user, Role role) {
		return (user != null && user.isLocalAccount()) || findProvider(user).supportsRoleChanges(user, role);
	}

	/**
	 * Returns true if the team's role can be changed.
	 *
	 * @param user
	 * @return true if the team's role can be changed
	 */
	@Override
	public boolean supportsRoleChanges(TeamModel team, Role role) {
		return (team != null && team.isLocalTeam()) || findProvider(team).supportsRoleChanges(team, role);
	}

	protected AuthenticationProvider findProvider(UserModel user) {
		for (AuthenticationProvider provider : authenticationProviders) {
			if (provider.getAccountType().equals(user.accountType)) {
				return provider;
			}
		}
		return AuthenticationProvider.NULL_PROVIDER;
	}

	protected AuthenticationProvider findProvider(TeamModel team) {
		for (AuthenticationProvider provider : authenticationProviders) {
			if (provider.getAccountType().equals(team.accountType)) {
				return provider;
			}
		}
		return AuthenticationProvider.NULL_PROVIDER;
	}
}
