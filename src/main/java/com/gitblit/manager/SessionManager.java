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
import java.util.List;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.wicket.RequestCycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.Constants;
import com.gitblit.Constants.AuthenticationType;
import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.models.UserModel;
import com.gitblit.utils.Base64;
import com.gitblit.utils.HttpUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.utils.X509Utils.X509Metadata;
import com.gitblit.wicket.GitBlitWebSession;

/**
 * The session manager handles user login & logout.
 *
 * @author James Moger
 *
 */
public class SessionManager implements ISessionManager {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final IStoredSettings settings;

	private final IRuntimeManager runtimeManager;

	private final IUserManager userManager;

	public SessionManager(
			IRuntimeManager runtimeManager,
			IUserManager userManager) {

		this.settings = runtimeManager.getSettings();
		this.runtimeManager = runtimeManager;
		this.userManager = userManager;
	}

	@Override
	public SessionManager start() {
		List<String> services = settings.getStrings("realm.authenticationServices");
		for (String service : services) {
			// TODO populate authentication services here
		}
		return this;
	}

	@Override
	public SessionManager stop() {
		return this;
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
	 * Authentication by X509Certificate, servlet container principal, cookie,
	 * and BASIC header.
	 *
	 * @param httpRequest
	 * @param requiresCertificate
	 * @return a user object or null
	 */
	@Override
	public UserModel authenticate(HttpServletRequest httpRequest, boolean requiresCertificate) {
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
				return user;
			} else {
				logger.warn(MessageFormat.format("Failed to find UserModel for {0}, attempted client certificate ({1}) authentication from {2}",
						model.username, metadata.serialNumber, httpRequest.getRemoteAddr()));
			}
		}

		if (requiresCertificate) {
			// caller requires client certificate authentication (e.g. git servlet)
			return null;
		}

		// try to authenticate by servlet container principal
		Principal principal = httpRequest.getUserPrincipal();
		if (principal != null) {
			String username = principal.getName();
			if (!StringUtils.isEmpty(username)) {
				boolean internalAccount = isInternalAccount(username);
				UserModel user = userManager.getUserModel(username);
				if (user != null) {
					// existing user
					flagWicketSession(AuthenticationType.CONTAINER);
					logger.debug(MessageFormat.format("{0} authenticated by servlet container principal from {1}",
							user.username, httpRequest.getRemoteAddr()));
					return user;
				} else if (settings.getBoolean(Keys.realm.container.autoCreateAccounts, false)
						&& !internalAccount) {
					// auto-create user from an authenticated container principal
					user = new UserModel(username.toLowerCase());
					user.displayName = username;
					user.password = Constants.EXTERNAL_ACCOUNT;
					userManager.updateUserModel(user);
					flagWicketSession(AuthenticationType.CONTAINER);
					logger.debug(MessageFormat.format("{0} authenticated and created by servlet container principal from {1}",
							user.username, httpRequest.getRemoteAddr()));
					return user;
				} else if (!internalAccount) {
					logger.warn(MessageFormat.format("Failed to find UserModel for {0}, attempted servlet container authentication from {1}",
							principal.getName(), httpRequest.getRemoteAddr()));
				}
			}
		}

		// try to authenticate by cookie
		if (userManager.supportsCookies()) {
			UserModel user = authenticate(httpRequest.getCookies());
			if (user != null) {
				flagWicketSession(AuthenticationType.COOKIE);
				logger.debug(MessageFormat.format("{0} authenticated by cookie from {1}",
						user.username, httpRequest.getRemoteAddr()));
				return user;
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
				UserModel user = authenticate(username, password);
				if (user != null) {
					flagWicketSession(AuthenticationType.CREDENTIALS);
					logger.debug(MessageFormat.format("{0} authenticated by BASIC request header from {1}",
							user.username, httpRequest.getRemoteAddr()));
					return user;
				} else {
					logger.warn(MessageFormat.format("Failed login attempt for {0}, invalid credentials from {1}",
							username, httpRequest.getRemoteAddr()));
				}
			}
		}
		return null;
	}

	/**
	 * Authenticate a user based on their cookie.
	 *
	 * @param cookies
	 * @return a user object or null
	 */
	protected UserModel authenticate(Cookie[] cookies) {
		if (userManager.supportsCookies()) {
			if (cookies != null && cookies.length > 0) {
				for (Cookie cookie : cookies) {
					if (cookie.getName().equals(Constants.NAME)) {
						String value = cookie.getValue();
						return userManager.authenticate(value.toCharArray());
					}
				}
			}
		}
		return null;
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
		// check to see if this is the federation user
//		if (canFederate()) {
//			if (usernameDecoded.equalsIgnoreCase(Constants.FEDERATION_USER)) {
//				List<String> tokens = getFederationTokens();
//				if (tokens.contains(pw)) {
//					return getFederationUser();
//				}
//			}
//		}

		UserModel user = userManager.authenticate(usernameDecoded, password);

		// try registered external authentication providers
		if (user == null) {
//			for (AuthenticationService service : authenticationServices) {
//				if (service instanceof UsernamePasswordAuthenticationService) {
//					user = service.authenticate(usernameDecoded, password);
//					if (user != null) {
//						// user authenticated
//						user.accountType = service.getAccountType();
//						return user;
//					}
//				}
//			}
		}
		return user;
	}

	/**
	 * Sets a cookie for the specified user.
	 *
	 * @param response
	 * @param user
	 */
	@Override
	public void setCookie(HttpServletResponse response, UserModel user) {
		GitBlitWebSession session = GitBlitWebSession.get();
		boolean standardLogin = session.authenticationType.isStandard();

		if (userManager.supportsCookies() && standardLogin) {
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
					userCookie.setMaxAge(Integer.MAX_VALUE);
				}
			}
			userCookie.setPath("/");
			response.addCookie(userCookie);
		}
	}

	/**
	 * Logout a user.
	 *
	 * @param user
	 */
	@Override
	public void logout(HttpServletResponse response, UserModel user) {
		setCookie(response,  null);
		userManager.logout(user);
	}

	/**
	 * Returns true if the username represents an internal account
	 *
	 * @param username
	 * @return true if the specified username represents an internal account
	 */
	protected boolean isInternalAccount(String username) {
		return !StringUtils.isEmpty(username)
				&& (username.equalsIgnoreCase(Constants.FEDERATION_USER)
						|| username.equalsIgnoreCase(UserModel.ANONYMOUS.username));
	}

//	protected UserModel getFederationUser() {
//		// the federation user is an administrator
//		UserModel federationUser = new UserModel(Constants.FEDERATION_USER);
//		federationUser.canAdmin = true;
//		return federationUser;
//	}
}
