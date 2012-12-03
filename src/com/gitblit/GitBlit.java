/*
 * Copyright 2011 gitblit.com.
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
package com.gitblit;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.security.Principal;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

import org.apache.wicket.RequestCycle;
import org.apache.wicket.protocol.http.WebResponse;
import org.apache.wicket.resource.ContextRelativeResource;
import org.apache.wicket.util.resource.ResourceStreamNotFoundException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.lib.RepositoryCache.FileKey;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.storage.file.WindowCache;
import org.eclipse.jgit.storage.file.WindowCacheConfig;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.Constants.AccessPermission;
import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.Constants.AuthenticationType;
import com.gitblit.Constants.AuthorizationControl;
import com.gitblit.Constants.FederationRequest;
import com.gitblit.Constants.FederationStrategy;
import com.gitblit.Constants.FederationToken;
import com.gitblit.Constants.PermissionType;
import com.gitblit.Constants.RegistrantType;
import com.gitblit.models.FederationModel;
import com.gitblit.models.FederationProposal;
import com.gitblit.models.FederationSet;
import com.gitblit.models.ForkModel;
import com.gitblit.models.Metric;
import com.gitblit.models.ProjectModel;
import com.gitblit.models.RegistrantAccessPermission;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.SearchResult;
import com.gitblit.models.ServerSettings;
import com.gitblit.models.ServerStatus;
import com.gitblit.models.SettingModel;
import com.gitblit.models.TeamModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.Base64;
import com.gitblit.utils.ByteFormat;
import com.gitblit.utils.ContainerUtils;
import com.gitblit.utils.DeepCopier;
import com.gitblit.utils.FederationUtils;
import com.gitblit.utils.HttpUtils;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.JsonUtils;
import com.gitblit.utils.MetricUtils;
import com.gitblit.utils.ObjectCache;
import com.gitblit.utils.StringUtils;
import com.gitblit.utils.TimeUtils;
import com.gitblit.utils.X509Utils.X509Metadata;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.WicketUtils;

/**
 * GitBlit is the servlet context listener singleton that acts as the core for
 * the web ui and the servlets. This class is either directly instantiated by
 * the GitBlitServer class (Gitblit GO) or is reflectively instantiated from the
 * definition in the web.xml file (Gitblit WAR).
 * 
 * This class is the central logic processor for Gitblit. All settings, user
 * object, and repository object operations pass through this class.
 * 
 * Repository Resolution. There are two pathways for finding repositories. One
 * pathway, for web ui display and repository authentication & authorization, is
 * within this class. The other pathway is through the standard GitServlet.
 * 
 * @author James Moger
 * 
 */
public class GitBlit implements ServletContextListener {

	private static GitBlit gitblit;
	
	private final Logger logger = LoggerFactory.getLogger(GitBlit.class);

	private final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(5);

	private final List<FederationModel> federationRegistrations = Collections
			.synchronizedList(new ArrayList<FederationModel>());

	private final Map<String, FederationModel> federationPullResults = new ConcurrentHashMap<String, FederationModel>();

	private final ObjectCache<Long> repositorySizeCache = new ObjectCache<Long>();

	private final ObjectCache<List<Metric>> repositoryMetricsCache = new ObjectCache<List<Metric>>();
	
	private final Map<String, RepositoryModel> repositoryListCache = new ConcurrentHashMap<String, RepositoryModel>();
	
	private final Map<String, ProjectModel> projectCache = new ConcurrentHashMap<String, ProjectModel>();
	
	private final AtomicReference<String> repositoryListSettingsChecksum = new AtomicReference<String>("");

	private ServletContext servletContext;

	private File repositoriesFolder;

	private IUserService userService;

	private IStoredSettings settings;

	private ServerSettings settingsModel;

	private ServerStatus serverStatus;

	private MailExecutor mailExecutor;
	
	private LuceneExecutor luceneExecutor;
	
	private GCExecutor gcExecutor;
	
	private TimeZone timezone;
	
	private FileBasedConfig projectConfigs;

	public GitBlit() {
		if (gitblit == null) {
			// set the static singleton reference
			gitblit = this;
		}
	}

	public GitBlit(final IUserService userService) {
		this.userService = userService;
		gitblit = this;
	}

	/**
	 * Returns the Gitblit singleton.
	 * 
	 * @return gitblit singleton
	 */
	public static GitBlit self() {
		if (gitblit == null) {
			new GitBlit();
		}
		return gitblit;
	}

	/**
	 * Determine if this is the GO variant of Gitblit.
	 * 
	 * @return true if this is the GO variant of Gitblit.
	 */
	public static boolean isGO() {
		return self().settings instanceof FileSettings;
	}
	
	/**
	 * Returns the preferred timezone for the Gitblit instance.
	 * 
	 * @return a timezone
	 */
	public static TimeZone getTimezone() {
		if (self().timezone == null) {
			String tzid = getString("web.timezone", null);
			if (StringUtils.isEmpty(tzid)) {
				self().timezone = TimeZone.getDefault();
				return self().timezone;
			}
			self().timezone = TimeZone.getTimeZone(tzid);
		}
		return self().timezone;
	}
	
	/**
	 * Returns the user-defined blob encodings.
	 * 
	 * @return an array of encodings, may be empty
	 */
	public static String [] getEncodings() {
		return getStrings(Keys.web.blobEncodings).toArray(new String[0]);
	}
	

	/**
	 * Returns the boolean value for the specified key. If the key does not
	 * exist or the value for the key can not be interpreted as a boolean, the
	 * defaultValue is returned.
	 * 
	 * @see IStoredSettings.getBoolean(String, boolean)
	 * @param key
	 * @param defaultValue
	 * @return key value or defaultValue
	 */
	public static boolean getBoolean(String key, boolean defaultValue) {
		return self().settings.getBoolean(key, defaultValue);
	}

	/**
	 * Returns the integer value for the specified key. If the key does not
	 * exist or the value for the key can not be interpreted as an integer, the
	 * defaultValue is returned.
	 * 
	 * @see IStoredSettings.getInteger(String key, int defaultValue)
	 * @param key
	 * @param defaultValue
	 * @return key value or defaultValue
	 */
	public static int getInteger(String key, int defaultValue) {
		return self().settings.getInteger(key, defaultValue);
	}
	
	/**
	 * Returns the value in bytes for the specified key. If the key does not
	 * exist or the value for the key can not be interpreted as an integer, the
	 * defaultValue is returned.
	 * 
	 * @see IStoredSettings.getFilesize(String key, int defaultValue)
	 * @param key
	 * @param defaultValue
	 * @return key value or defaultValue
	 */
	public static int getFilesize(String key, int defaultValue) {
		return self().settings.getFilesize(key, defaultValue);
	}

	/**
	 * Returns the value in bytes for the specified key. If the key does not
	 * exist or the value for the key can not be interpreted as a long, the
	 * defaultValue is returned.
	 * 
	 * @see IStoredSettings.getFilesize(String key, long defaultValue)
	 * @param key
	 * @param defaultValue
	 * @return key value or defaultValue
	 */
	public static long getFilesize(String key, long defaultValue) {
		return self().settings.getFilesize(key, defaultValue);
	}

	/**
	 * Returns the char value for the specified key. If the key does not exist
	 * or the value for the key can not be interpreted as a character, the
	 * defaultValue is returned.
	 * 
	 * @see IStoredSettings.getChar(String key, char defaultValue)
	 * @param key
	 * @param defaultValue
	 * @return key value or defaultValue
	 */
	public static char getChar(String key, char defaultValue) {
		return self().settings.getChar(key, defaultValue);
	}

	/**
	 * Returns the string value for the specified key. If the key does not exist
	 * or the value for the key can not be interpreted as a string, the
	 * defaultValue is returned.
	 * 
	 * @see IStoredSettings.getString(String key, String defaultValue)
	 * @param key
	 * @param defaultValue
	 * @return key value or defaultValue
	 */
	public static String getString(String key, String defaultValue) {
		return self().settings.getString(key, defaultValue);
	}

	/**
	 * Returns a list of space-separated strings from the specified key.
	 * 
	 * @see IStoredSettings.getStrings(String key)
	 * @param name
	 * @return list of strings
	 */
	public static List<String> getStrings(String key) {
		return self().settings.getStrings(key);
	}

	/**
	 * Returns a map of space-separated key-value pairs from the specified key.
	 * 
	 * @see IStoredSettings.getStrings(String key)
	 * @param name
	 * @return map of string, string
	 */
	public static Map<String, String> getMap(String key) {
		return self().settings.getMap(key);
	}

	/**
	 * Returns the list of keys whose name starts with the specified prefix. If
	 * the prefix is null or empty, all key names are returned.
	 * 
	 * @see IStoredSettings.getAllKeys(String key)
	 * @param startingWith
	 * @return list of keys
	 */

	public static List<String> getAllKeys(String startingWith) {
		return self().settings.getAllKeys(startingWith);
	}

	/**
	 * Is Gitblit running in debug mode?
	 * 
	 * @return true if Gitblit is running in debug mode
	 */
	public static boolean isDebugMode() {
		return self().settings.getBoolean(Keys.web.debugMode, false);
	}

	/**
	 * Returns the file object for the specified configuration key.
	 * 
	 * @return the file
	 */
	public static File getFileOrFolder(String key, String defaultFileOrFolder) {
		String fileOrFolder = GitBlit.getString(key, defaultFileOrFolder);
		return getFileOrFolder(fileOrFolder);
	}

	/**
	 * Returns the file object which may have it's base-path determined by
	 * environment variables for running on a cloud hosting service. All Gitblit
	 * file or folder retrievals are (at least initially) funneled through this
	 * method so it is the correct point to globally override/alter filesystem
	 * access based on environment or some other indicator.
	 * 
	 * @return the file
	 */
	public static File getFileOrFolder(String fileOrFolder) {
		String openShift = System.getenv("OPENSHIFT_DATA_DIR");
		if (!StringUtils.isEmpty(openShift)) {
			// running on RedHat OpenShift
			return new File(openShift, fileOrFolder);
		}
		return new File(fileOrFolder);
	}

	/**
	 * Returns the path of the repositories folder. This method checks to see if
	 * Gitblit is running on a cloud service and may return an adjusted path.
	 * 
	 * @return the repositories folder path
	 */
	public static File getRepositoriesFolder() {
		return getFileOrFolder(Keys.git.repositoriesFolder, "git");
	}

	/**
	 * Returns the path of the proposals folder. This method checks to see if
	 * Gitblit is running on a cloud service and may return an adjusted path.
	 * 
	 * @return the proposals folder path
	 */
	public static File getProposalsFolder() {
		return getFileOrFolder(Keys.federation.proposalsFolder, "proposals");
	}

	/**
	 * Returns the path of the Groovy folder. This method checks to see if
	 * Gitblit is running on a cloud service and may return an adjusted path.
	 * 
	 * @return the Groovy scripts folder path
	 */
	public static File getGroovyScriptsFolder() {
		return getFileOrFolder(Keys.groovy.scriptsFolder, "groovy");
	}

	/**
	 * Updates the list of server settings.
	 * 
	 * @param settings
	 * @return true if the update succeeded
	 */
	public boolean updateSettings(Map<String, String> updatedSettings) {
		return settings.saveSettings(updatedSettings);
	}

	public ServerStatus getStatus() {
		// update heap memory status
		serverStatus.heapAllocated = Runtime.getRuntime().totalMemory();
		serverStatus.heapFree = Runtime.getRuntime().freeMemory();
		return serverStatus;
	}

	/**
	 * Returns the list of non-Gitblit clone urls. This allows Gitblit to
	 * advertise alternative urls for Git client repository access.
	 * 
	 * @param repositoryName
	 * @return list of non-gitblit clone urls
	 */
	public List<String> getOtherCloneUrls(String repositoryName) {
		List<String> cloneUrls = new ArrayList<String>();
		for (String url : settings.getStrings(Keys.web.otherUrls)) {
			cloneUrls.add(MessageFormat.format(url, repositoryName));
		}
		return cloneUrls;
	}

	/**
	 * Set the user service. The user service authenticates all users and is
	 * responsible for managing user permissions.
	 * 
	 * @param userService
	 */
	public void setUserService(IUserService userService) {
		logger.info("Setting up user service " + userService.toString());
		this.userService = userService;
		this.userService.setup(settings);
	}
	
	/**
	 * 
	 * @return true if the user service supports credential changes
	 */
	public boolean supportsCredentialChanges() {
		return userService.supportsCredentialChanges();
	}

	/**
	 * 
	 * @return true if the user service supports display name changes
	 */
	public boolean supportsDisplayNameChanges() {
		return userService.supportsDisplayNameChanges();
	}

	/**
	 * 
	 * @return true if the user service supports email address changes
	 */
	public boolean supportsEmailAddressChanges() {
		return userService.supportsEmailAddressChanges();
	}

	/**
	 * 
	 * @return true if the user service supports team membership changes
	 */
	public boolean supportsTeamMembershipChanges() {
		return userService.supportsTeamMembershipChanges();
	}

	/**
	 * Authenticate a user based on a username and password.
	 * 
	 * @see IUserService.authenticate(String, char[])
	 * @param username
	 * @param password
	 * @return a user object or null
	 */
	public UserModel authenticate(String username, char[] password) {
		if (StringUtils.isEmpty(username)) {
			// can not authenticate empty username
			return null;
		}
		String pw = new String(password);
		if (StringUtils.isEmpty(pw)) {
			// can not authenticate empty password
			return null;
		}

		// check to see if this is the federation user
		if (canFederate()) {
			if (username.equalsIgnoreCase(Constants.FEDERATION_USER)) {
				List<String> tokens = getFederationTokens();
				if (tokens.contains(pw)) {
					// the federation user is an administrator
					UserModel federationUser = new UserModel(Constants.FEDERATION_USER);
					federationUser.canAdmin = true;
					return federationUser;
				}
			}
		}

		// delegate authentication to the user service
		if (userService == null) {
			return null;
		}
		return userService.authenticate(username, password);
	}

	/**
	 * Authenticate a user based on their cookie.
	 * 
	 * @param cookies
	 * @return a user object or null
	 */
	protected UserModel authenticate(Cookie[] cookies) {
		if (userService == null) {
			return null;
		}
		if (userService.supportsCookies()) {
			if (cookies != null && cookies.length > 0) {
				for (Cookie cookie : cookies) {
					if (cookie.getName().equals(Constants.NAME)) {
						String value = cookie.getValue();
						return userService.authenticate(value.toCharArray());
					}
				}
			}
		}
		return null;
	}

	/**
	 * Authenticate a user based on HTTP request parameters.
	 * 
	 * Authentication by X509Certificate is tried first and then by cookie.
	 * 
	 * @param httpRequest
	 * @return a user object or null
	 */
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
	public UserModel authenticate(HttpServletRequest httpRequest, boolean requiresCertificate) {
		// try to authenticate by certificate
		boolean checkValidity = settings.getBoolean(Keys.git.enforceCertificateValidity, true);
		String [] oids = getStrings(Keys.git.certificateUsernameOIDs).toArray(new String[0]);
		UserModel model = HttpUtils.getUserModelFromCertificate(httpRequest, checkValidity, oids);
		if (model != null) {
			// grab real user model and preserve certificate serial number
			UserModel user = getUserModel(model.username);
			X509Metadata metadata = HttpUtils.getCertificateMetadata(httpRequest);
			if (user != null) {
				flagWicketSession(AuthenticationType.CERTIFICATE);
				logger.info(MessageFormat.format("{0} authenticated by client certificate {1} from {2}",
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
			UserModel user = getUserModel(principal.getName());
			if (user != null) {
				flagWicketSession(AuthenticationType.CONTAINER);
				logger.info(MessageFormat.format("{0} authenticated by servlet container principal from {1}",
						user.username, httpRequest.getRemoteAddr()));
				return user;
			} else {
				logger.warn(MessageFormat.format("Failed to find UserModel for {0}, attempted servlet container authentication from {1}",
						principal.getName(), httpRequest.getRemoteAddr()));
			}
		}
		
		// try to authenticate by cookie
		if (allowCookieAuthentication()) {
			UserModel user = authenticate(httpRequest.getCookies());
			if (user != null) {
				flagWicketSession(AuthenticationType.COOKIE);
				logger.info(MessageFormat.format("{0} authenticated by cookie from {1}",
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
			final String[] values = credentials.split(":",2);

			if (values.length == 2) {
				String username = values[0];
				char[] password = values[1].toCharArray();
				UserModel user = authenticate(username, password);
				if (user != null) {
					flagWicketSession(AuthenticationType.CREDENTIALS);
					logger.info(MessageFormat.format("{0} authenticated by BASIC request header from {1}",
							user.username, httpRequest.getRemoteAddr()));
					return user;
				} else {
					logger.warn(MessageFormat.format("Failed login attempt for {0}, invalid credentials ({1}) from {2}", 
							username, credentials, httpRequest.getRemoteAddr()));
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
	 * Open a file resource using the Servlet container.
	 * @param file to open
	 * @return InputStream of the opened file
	 * @throws ResourceStreamNotFoundException
	 */
	public InputStream getResourceAsStream(String file) throws ResourceStreamNotFoundException {
		ContextRelativeResource res = WicketUtils.getResource(file);
		return res.getResourceStream().getInputStream();
	}

	/**
	 * Sets a cookie for the specified user.
	 * 
	 * @param response
	 * @param user
	 */
	public void setCookie(WebResponse response, UserModel user) {
		if (userService == null) {
			return;
		}
		if (userService.supportsCookies()) {
			Cookie userCookie;
			if (user == null) {
				// clear cookie for logout
				userCookie = new Cookie(Constants.NAME, "");
			} else {
				// set cookie for login
				String cookie = userService.getCookie(user);
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
	public void logout(UserModel user) {
		if (userService == null) {
			return;
		}
		userService.logout(user);
	}

	/**
	 * Returns the list of all users available to the login service.
	 * 
	 * @see IUserService.getAllUsernames()
	 * @return list of all usernames
	 */
	public List<String> getAllUsernames() {
		List<String> names = new ArrayList<String>(userService.getAllUsernames());
		return names;
	}

	/**
	 * Returns the list of all users available to the login service.
	 * 
	 * @see IUserService.getAllUsernames()
	 * @return list of all usernames
	 */
	public List<UserModel> getAllUsers() {
		List<UserModel> users = userService.getAllUsers();
		return users;
	}

	/**
	 * Delete the user object with the specified username
	 * 
	 * @see IUserService.deleteUser(String)
	 * @param username
	 * @return true if successful
	 */
	public boolean deleteUser(String username) {
		if (StringUtils.isEmpty(username)) {
			return false;
		}
		return userService.deleteUser(username);
	}

	/**
	 * Retrieve the user object for the specified username.
	 * 
	 * @see IUserService.getUserModel(String)
	 * @param username
	 * @return a user object or null
	 */
	public UserModel getUserModel(String username) {
		if (StringUtils.isEmpty(username)) {
			return null;
		}
		UserModel user = userService.getUserModel(username);		
		return user;
	}
	
	/**
	 * Returns the effective list of permissions for this user, taking into account
	 * team memberships, ownerships.
	 * 
	 * @param user
	 * @return the effective list of permissions for the user
	 */
	public List<RegistrantAccessPermission> getUserAccessPermissions(UserModel user) {
		Set<RegistrantAccessPermission> set = new LinkedHashSet<RegistrantAccessPermission>();
		set.addAll(user.getRepositoryPermissions());
		// Flag missing repositories
		for (RegistrantAccessPermission permission : set) {
			if (permission.mutable && PermissionType.EXPLICIT.equals(permission.permissionType)) {
				RepositoryModel rm = GitBlit.self().getRepositoryModel(permission.registrant);
				if (rm == null) {
					permission.permissionType = PermissionType.MISSING;
					permission.mutable = false;
					continue;
				}
			}
		}

		// TODO reconsider ownership as a user property
		// manually specify personal repository ownerships
		for (RepositoryModel rm : repositoryListCache.values()) {
			if (rm.isUsersPersonalRepository(user.username) || rm.isOwner(user.username)) {
				RegistrantAccessPermission rp = new RegistrantAccessPermission(rm.name, AccessPermission.REWIND,
						PermissionType.OWNER, RegistrantType.REPOSITORY, null, false);
				// user may be owner of a repository to which they've inherited
				// a team permission, replace any existing perm with owner perm
				set.remove(rp);
				set.add(rp);
			}
		}
		
		List<RegistrantAccessPermission> list = new ArrayList<RegistrantAccessPermission>(set);
		Collections.sort(list);
		return list;
	}

	/**
	 * Returns the list of users and their access permissions for the specified
	 * repository including permission source information such as the team or
	 * regular expression which sets the permission.
	 * 
	 * @param repository
	 * @return a list of RegistrantAccessPermissions
	 */
	public List<RegistrantAccessPermission> getUserAccessPermissions(RepositoryModel repository) {
		List<RegistrantAccessPermission> list = new ArrayList<RegistrantAccessPermission>();
		if (AccessRestrictionType.NONE.equals(repository.accessRestriction)) {
			// no permissions needed, REWIND for everyone!
			return list;
		}
		if (AuthorizationControl.AUTHENTICATED.equals(repository.authorizationControl)) {
			// no permissions needed, REWIND for authenticated!
			return list;
		}
		// NAMED users and teams
		for (UserModel user : userService.getAllUsers()) {
			RegistrantAccessPermission ap = user.getRepositoryPermission(repository);
			if (ap.permission.exceeds(AccessPermission.NONE)) {
				list.add(ap);
			}
		}
		return list;
	}
	
	/**
	 * Sets the access permissions to the specified repository for the specified users.
	 * 
	 * @param repository
	 * @param permissions
	 * @return true if the user models have been updated
	 */
	public boolean setUserAccessPermissions(RepositoryModel repository, Collection<RegistrantAccessPermission> permissions) {
		List<UserModel> users = new ArrayList<UserModel>();
		for (RegistrantAccessPermission up : permissions) {
			if (up.mutable) {
				// only set editable defined permissions
				UserModel user = userService.getUserModel(up.registrant);
				user.setRepositoryPermission(repository.name, up.permission);
				users.add(user);
			}
		}
		return userService.updateUserModels(users);
	}
	
	/**
	 * Returns the list of all users who have an explicit access permission
	 * for the specified repository.
	 * 
	 * @see IUserService.getUsernamesForRepositoryRole(String)
	 * @param repository
	 * @return list of all usernames that have an access permission for the repository
	 */
	public List<String> getRepositoryUsers(RepositoryModel repository) {
		return userService.getUsernamesForRepositoryRole(repository.name);
	}

	/**
	 * Sets the list of all uses who are allowed to bypass the access
	 * restriction placed on the specified repository.
	 * 
	 * @see IUserService.setUsernamesForRepositoryRole(String, List<String>)
	 * @param repository
	 * @param usernames
	 * @return true if successful
	 */
	@Deprecated
	public boolean setRepositoryUsers(RepositoryModel repository, List<String> repositoryUsers) {
		// rejects all changes since 1.2.0 because this would elevate
		// all discrete access permissions to RW+
		return false;
	}

	/**
	 * Adds/updates a complete user object keyed by username. This method allows
	 * for renaming a user.
	 * 
	 * @see IUserService.updateUserModel(String, UserModel)
	 * @param username
	 * @param user
	 * @param isCreate
	 * @throws GitBlitException
	 */
	public void updateUserModel(String username, UserModel user, boolean isCreate)
			throws GitBlitException {
		if (!username.equalsIgnoreCase(user.username)) {
			if (userService.getUserModel(user.username) != null) {
				throw new GitBlitException(MessageFormat.format(
						"Failed to rename ''{0}'' because ''{1}'' already exists.", username,
						user.username));
			}
			
			// rename repositories and owner fields for all repositories
			for (RepositoryModel model : getRepositoryModels(user)) {
				if (model.isUsersPersonalRepository(username)) {
					// personal repository
					model.owner = user.username;
					String oldRepositoryName = model.name;
					model.name = "~" + user.username + model.name.substring(model.projectPath.length());
					model.projectPath = "~" + user.username;
					updateRepositoryModel(oldRepositoryName, model, false);
				} else if (model.isOwner(username)) {
					// common/shared repo
					model.owner = user.username;
					updateRepositoryModel(model.name, model, false);
				}
			}
		}
		if (!userService.updateUserModel(username, user)) {
			throw new GitBlitException(isCreate ? "Failed to add user!" : "Failed to update user!");
		}
	}

	/**
	 * Returns the list of available teams that a user or repository may be
	 * assigned to.
	 * 
	 * @return the list of teams
	 */
	public List<String> getAllTeamnames() {
		List<String> teams = new ArrayList<String>(userService.getAllTeamNames());
		return teams;
	}

	/**
	 * Returns the list of available teams that a user or repository may be
	 * assigned to.
	 * 
	 * @return the list of teams
	 */
	public List<TeamModel> getAllTeams() {
		List<TeamModel> teams = userService.getAllTeams();
		return teams;
	}

	/**
	 * Returns the TeamModel object for the specified name.
	 * 
	 * @param teamname
	 * @return a TeamModel object or null
	 */
	public TeamModel getTeamModel(String teamname) {
		return userService.getTeamModel(teamname);
	}
	
	/**
	 * Returns the list of teams and their access permissions for the specified
	 * repository including the source of the permission such as the admin flag
	 * or a regular expression.
	 * 
	 * @param repository
	 * @return a list of RegistrantAccessPermissions
	 */
	public List<RegistrantAccessPermission> getTeamAccessPermissions(RepositoryModel repository) {
		List<RegistrantAccessPermission> list = new ArrayList<RegistrantAccessPermission>();
		for (TeamModel team : userService.getAllTeams()) {
			RegistrantAccessPermission ap = team.getRepositoryPermission(repository);
			if (ap.permission.exceeds(AccessPermission.NONE)) {
				list.add(ap);
			}
		}
		Collections.sort(list);
		return list;
	}
	
	/**
	 * Sets the access permissions to the specified repository for the specified teams.
	 * 
	 * @param repository
	 * @param permissions
	 * @return true if the team models have been updated
	 */
	public boolean setTeamAccessPermissions(RepositoryModel repository, Collection<RegistrantAccessPermission> permissions) {
		List<TeamModel> teams = new ArrayList<TeamModel>();
		for (RegistrantAccessPermission tp : permissions) {
			if (tp.mutable) {
				// only set explicitly defined access permissions
				TeamModel team = userService.getTeamModel(tp.registrant);
				team.setRepositoryPermission(repository.name, tp.permission);
				teams.add(team);
			}
		}
		return userService.updateTeamModels(teams);
	}
	
	/**
	 * Returns the list of all teams who have an explicit access permission for
	 * the specified repository.
	 * 
	 * @see IUserService.getTeamnamesForRepositoryRole(String)
	 * @param repository
	 * @return list of all teamnames with explicit access permissions to the repository
	 */
	public List<String> getRepositoryTeams(RepositoryModel repository) {
		return userService.getTeamnamesForRepositoryRole(repository.name);
	}

	/**
	 * Sets the list of all uses who are allowed to bypass the access
	 * restriction placed on the specified repository.
	 * 
	 * @see IUserService.setTeamnamesForRepositoryRole(String, List<String>)
	 * @param repository
	 * @param teamnames
	 * @return true if successful
	 */
	@Deprecated
	public boolean setRepositoryTeams(RepositoryModel repository, List<String> repositoryTeams) {
		// rejects all changes since 1.2.0 because this would elevate
		// all discrete access permissions to RW+
		return false;
	}

	/**
	 * Updates the TeamModel object for the specified name.
	 * 
	 * @param teamname
	 * @param team
	 * @param isCreate
	 */
	public void updateTeamModel(String teamname, TeamModel team, boolean isCreate)
			throws GitBlitException {
		if (!teamname.equalsIgnoreCase(team.name)) {
			if (userService.getTeamModel(team.name) != null) {
				throw new GitBlitException(MessageFormat.format(
						"Failed to rename ''{0}'' because ''{1}'' already exists.", teamname,
						team.name));
			}
		}
		if (!userService.updateTeamModel(teamname, team)) {
			throw new GitBlitException(isCreate ? "Failed to add team!" : "Failed to update team!");
		}
	}

	/**
	 * Delete the team object with the specified teamname
	 * 
	 * @see IUserService.deleteTeam(String)
	 * @param teamname
	 * @return true if successful
	 */
	public boolean deleteTeam(String teamname) {
		return userService.deleteTeam(teamname);
	}
	
	/**
	 * Adds the repository to the list of cached repositories if Gitblit is
	 * configured to cache the repository list.
	 * 
	 * @param model
	 */
	private void addToCachedRepositoryList(RepositoryModel model) {
		if (settings.getBoolean(Keys.git.cacheRepositoryList, true)) {
			repositoryListCache.put(model.name.toLowerCase(), model);
			
			// update the fork origin repository with this repository clone
			if (!StringUtils.isEmpty(model.originRepository)) {
				if (repositoryListCache.containsKey(model.originRepository)) {
					RepositoryModel origin = repositoryListCache.get(model.originRepository);
					origin.addFork(model.name);
				}
			}
		}
	}
	
	/**
	 * Removes the repository from the list of cached repositories.
	 * 
	 * @param name
	 * @return the model being removed
	 */
	private RepositoryModel removeFromCachedRepositoryList(String name) {
		if (StringUtils.isEmpty(name)) {
			return null;
		}
		return repositoryListCache.remove(name.toLowerCase());
	}

	/**
	 * Clears all the cached metadata for the specified repository.
	 * 
	 * @param repositoryName
	 */
	private void clearRepositoryMetadataCache(String repositoryName) {
		repositorySizeCache.remove(repositoryName);
		repositoryMetricsCache.remove(repositoryName);
	}
	
	/**
	 * Resets the repository list cache.
	 * 
	 */
	public void resetRepositoryListCache() {
		logger.info("Repository cache manually reset");
		repositoryListCache.clear();
	}
	
	/**
	 * Calculate the checksum of settings that affect the repository list cache.
	 * @return a checksum
	 */
	private String getRepositoryListSettingsChecksum() {
		StringBuilder ns = new StringBuilder();
		ns.append(settings.getString(Keys.git.cacheRepositoryList, "")).append('\n');
		ns.append(settings.getString(Keys.git.onlyAccessBareRepositories, "")).append('\n');
		ns.append(settings.getString(Keys.git.searchRepositoriesSubfolders, "")).append('\n');
		ns.append(settings.getString(Keys.git.searchRecursionDepth, "")).append('\n');
		ns.append(settings.getString(Keys.git.searchExclusions, "")).append('\n');
		String checksum = StringUtils.getSHA1(ns.toString());
		return checksum;
	}
	
	/**
	 * Compare the last repository list setting checksum to the current checksum.
	 * If different then clear the cache so that it may be rebuilt.
	 * 
	 * @return true if the cached repository list is valid since the last check
	 */
	private boolean isValidRepositoryList() {
		String newChecksum = getRepositoryListSettingsChecksum();
		boolean valid = newChecksum.equals(repositoryListSettingsChecksum.get());
		repositoryListSettingsChecksum.set(newChecksum);
		if (!valid && settings.getBoolean(Keys.git.cacheRepositoryList,  true)) {
			logger.info("Repository list settings have changed. Clearing repository list cache.");
			repositoryListCache.clear();
		}
		return valid;
	}

	/**
	 * Returns the list of all repositories available to Gitblit. This method
	 * does not consider user access permissions.
	 * 
	 * @return list of all repositories
	 */
	public List<String> getRepositoryList() {
		if (repositoryListCache.size() == 0 || !isValidRepositoryList()) {
			// we are not caching OR we have not yet cached OR the cached list is invalid
			long startTime = System.currentTimeMillis();
			List<String> repositories = JGitUtils.getRepositoryList(repositoriesFolder, 
					settings.getBoolean(Keys.git.onlyAccessBareRepositories, false),
					settings.getBoolean(Keys.git.searchRepositoriesSubfolders, true),
					settings.getInteger(Keys.git.searchRecursionDepth, -1),
					settings.getStrings(Keys.git.searchExclusions));

			if (!settings.getBoolean(Keys.git.cacheRepositoryList,  true)) {
				// we are not caching
				StringUtils.sortRepositorynames(repositories);
				return repositories;
			} else {
				// we are caching this list
				String msg = "{0} repositories identified in {1} msecs";

				// optionally (re)calculate repository sizes
				if (getBoolean(Keys.web.showRepositorySizes, true)) {
					msg = "{0} repositories identified with calculated folder sizes in {1} msecs";
					for (String repository : repositories) {
						RepositoryModel model = getRepositoryModel(repository);
						if (!model.skipSizeCalculation) {
							calculateSize(model);
						}
					}
				} else {
					// update cache
					for (String repository : repositories) {
						getRepositoryModel(repository);
					}
				}
				
				// rebuild fork networks
				for (RepositoryModel model : repositoryListCache.values()) {
					if (!StringUtils.isEmpty(model.originRepository)) {
						if (repositoryListCache.containsKey(model.originRepository)) {
							RepositoryModel origin = repositoryListCache.get(model.originRepository);
							origin.addFork(model.name);
						}
					}
				}
				
				long duration = System.currentTimeMillis() - startTime;
				logger.info(MessageFormat.format(msg, repositoryListCache.size(), duration));
			}
		}
		
		// return sorted copy of cached list
		List<String> list = new ArrayList<String>(repositoryListCache.keySet());		
		StringUtils.sortRepositorynames(list);
		return list;
	}

	/**
	 * Returns the JGit repository for the specified name.
	 * 
	 * @param repositoryName
	 * @return repository or null
	 */
	public Repository getRepository(String repositoryName) {
		return getRepository(repositoryName, true);
	}

	/**
	 * Returns the JGit repository for the specified name.
	 * 
	 * @param repositoryName
	 * @param logError
	 * @return repository or null
	 */
	public Repository getRepository(String repositoryName, boolean logError) {
		if (isCollectingGarbage(repositoryName)) {
			logger.warn(MessageFormat.format("Rejecting request for {0}, busy collecting garbage!", repositoryName));
			return null;
		}

		File dir = FileKey.resolve(new File(repositoriesFolder, repositoryName), FS.DETECTED);
		if (dir == null)
			return null;
		
		Repository r = null;
		try {
			FileKey key = FileKey.exact(dir, FS.DETECTED);
			r = RepositoryCache.open(key, true);
		} catch (IOException e) {
			if (logError) {
				logger.error("GitBlit.getRepository(String) failed to find "
						+ new File(repositoriesFolder, repositoryName).getAbsolutePath());
			}
		}
		return r;
	}

	/**
	 * Returns the list of repository models that are accessible to the user.
	 * 
	 * @param user
	 * @return list of repository models accessible to user
	 */
	public List<RepositoryModel> getRepositoryModels(UserModel user) {
		long methodStart = System.currentTimeMillis();
		List<String> list = getRepositoryList();
		List<RepositoryModel> repositories = new ArrayList<RepositoryModel>();
		for (String repo : list) {
			RepositoryModel model = getRepositoryModel(user, repo);
			if (model != null) {
				repositories.add(model);
			}
		}
		if (getBoolean(Keys.web.showRepositorySizes, true)) {
			int repoCount = 0;
			long startTime = System.currentTimeMillis();
			ByteFormat byteFormat = new ByteFormat();
			for (RepositoryModel model : repositories) {
				if (!model.skipSizeCalculation) {
					repoCount++;
					model.size = byteFormat.format(calculateSize(model));
				}
			}
			long duration = System.currentTimeMillis() - startTime;
			if (duration > 250) {
				// only log calcualtion time if > 250 msecs
				logger.info(MessageFormat.format("{0} repository sizes calculated in {1} msecs",
					repoCount, duration));
			}
		}
		long duration = System.currentTimeMillis() - methodStart;
		logger.info(MessageFormat.format("{0} repository models loaded for {1} in {2} msecs",
				repositories.size(), user == null ? "anonymous" : user.username, duration));
		return repositories;
	}

	/**
	 * Returns a repository model if the repository exists and the user may
	 * access the repository.
	 * 
	 * @param user
	 * @param repositoryName
	 * @return repository model or null
	 */
	public RepositoryModel getRepositoryModel(UserModel user, String repositoryName) {
		RepositoryModel model = getRepositoryModel(repositoryName);
		if (model == null) {
			return null;
		}
		if (user == null) {
			user = UserModel.ANONYMOUS;
		}
		if (user.canView(model)) {
			return model;
		}
		return null;
	}

	/**
	 * Returns the repository model for the specified repository. This method
	 * does not consider user access permissions.
	 * 
	 * @param repositoryName
	 * @return repository model or null
	 */
	public RepositoryModel getRepositoryModel(String repositoryName) {
		if (!repositoryListCache.containsKey(repositoryName)) {
			RepositoryModel model = loadRepositoryModel(repositoryName);
			if (model == null) {
				return null;
			}
			addToCachedRepositoryList(model);
			return model;
		}
		
		// cached model
		RepositoryModel model = repositoryListCache.get(repositoryName.toLowerCase());

		if (gcExecutor.isCollectingGarbage(model.name)) {
			// Gitblit is busy collecting garbage, use our cached model
			RepositoryModel rm = DeepCopier.copy(model);
			rm.isCollectingGarbage = true;
			return rm;
		}

		// check for updates
		Repository r = getRepository(repositoryName);
		if (r == null) {
			// repository is missing
			removeFromCachedRepositoryList(repositoryName);
			logger.error(MessageFormat.format("Repository \"{0}\" is missing! Removing from cache.", repositoryName));
			return null;
		}
		
		FileBasedConfig config = (FileBasedConfig) getRepositoryConfig(r);
		if (config.isOutdated()) {
			// reload model
			logger.info(MessageFormat.format("Config for \"{0}\" has changed. Reloading model and updating cache.", repositoryName));
			model = loadRepositoryModel(repositoryName);
			removeFromCachedRepositoryList(repositoryName);
			addToCachedRepositoryList(model);
		} else {
			// update a few repository parameters 
			if (!model.hasCommits) {
				// update hasCommits, assume a repository only gains commits :)
				model.hasCommits = JGitUtils.hasCommits(r);
			}

			model.lastChange = JGitUtils.getLastChange(r);
		}
		r.close();
		
		// return a copy of the cached model
		return DeepCopier.copy(model);
	}
	
	
	/**
	 * Returns the map of project config.  This map is cached and reloaded if
	 * the underlying projects.conf file changes.
	 * 
	 * @return project config map
	 */
	private Map<String, ProjectModel> getProjectConfigs() {
		if (projectCache.isEmpty() || projectConfigs.isOutdated()) {
			
			try {
				projectConfigs.load();
			} catch (Exception e) {
			}

			// project configs
			String rootName = GitBlit.getString(Keys.web.repositoryRootGroupName, "main");
			ProjectModel rootProject = new ProjectModel(rootName, true);

			Map<String, ProjectModel> configs = new HashMap<String, ProjectModel>();
			// cache the root project under its alias and an empty path
			configs.put("", rootProject);
			configs.put(rootProject.name.toLowerCase(), rootProject);

			for (String name : projectConfigs.getSubsections("project")) {
				ProjectModel project;
				if (name.equalsIgnoreCase(rootName)) {
					project = rootProject;
				} else {
					project = new ProjectModel(name);
				}
				project.title = projectConfigs.getString("project", name, "title");
				project.description = projectConfigs.getString("project", name, "description");
				configs.put(name.toLowerCase(), project);				
			}
			projectCache.clear();
			projectCache.putAll(configs);
		}
		return projectCache;
	}
	
	/**
	 * Returns a list of project models for the user.
	 * 
	 * @param user
	 * @param includeUsers
	 * @return list of projects that are accessible to the user
	 */
	public List<ProjectModel> getProjectModels(UserModel user, boolean includeUsers) {
		Map<String, ProjectModel> configs = getProjectConfigs();

		// per-user project lists, this accounts for security and visibility
		Map<String, ProjectModel> map = new TreeMap<String, ProjectModel>();
		// root project
		map.put("", configs.get(""));
		
		for (RepositoryModel model : getRepositoryModels(user)) {
			String rootPath = StringUtils.getRootPath(model.name).toLowerCase();			
			if (!map.containsKey(rootPath)) {
				ProjectModel project;
				if (configs.containsKey(rootPath)) {
					// clone the project model because it's repository list will
					// be tailored for the requesting user
					project = DeepCopier.copy(configs.get(rootPath));
				} else {
					project = new ProjectModel(rootPath);
				}
				map.put(rootPath, project);
			}
			map.get(rootPath).addRepository(model);
		}
		
		// sort projects, root project first
		List<ProjectModel> projects;
		if (includeUsers) {
			// all projects
			projects = new ArrayList<ProjectModel>(map.values());
			Collections.sort(projects);
			projects.remove(map.get(""));
			projects.add(0, map.get(""));
		} else {
			// all non-user projects
			projects = new ArrayList<ProjectModel>();
			ProjectModel root = map.remove("");
			for (ProjectModel model : map.values()) {
				if (!model.isUserProject()) {
					projects.add(model);
				}
			}
			Collections.sort(projects);
			projects.add(0, root);
		}
		return projects;
	}
	
	/**
	 * Returns the project model for the specified user.
	 * 
	 * @param name
	 * @param user
	 * @return a project model, or null if it does not exist
	 */
	public ProjectModel getProjectModel(String name, UserModel user) {
		for (ProjectModel project : getProjectModels(user, true)) {
			if (project.name.equalsIgnoreCase(name)) {
				return project;
			}
		}
		return null;
	}
	
	/**
	 * Returns a project model for the Gitblit/system user.
	 * 
	 * @param name a project name
	 * @return a project model or null if the project does not exist
	 */
	public ProjectModel getProjectModel(String name) {
		Map<String, ProjectModel> configs = getProjectConfigs();
		ProjectModel project = configs.get(name.toLowerCase());
		if (project == null) {
			project = new ProjectModel(name);
			if (name.length() > 0 && name.charAt(0) == '~') {
				UserModel user = getUserModel(name.substring(1));
				if (user != null) {
					project.title = user.getDisplayName();
					project.description = "personal repositories";
				}
			}
		} else {
			// clone the object
			project = DeepCopier.copy(project);
		}
		if (StringUtils.isEmpty(name)) {
			// get root repositories
			for (String repository : getRepositoryList()) {
				if (repository.indexOf('/') == -1) {
					project.addRepository(repository);
				}
			}
		} else {
			// get repositories in subfolder
			String folder = name.toLowerCase() + "/";
			for (String repository : getRepositoryList()) {
				if (repository.toLowerCase().startsWith(folder)) {
					project.addRepository(repository);
				}
			}
		}
		if (project.repositories.size() == 0) {
			// no repositories == no project
			return null;
		}
		return project;
	}
	
	/**
	 * Workaround JGit.  I need to access the raw config object directly in order
	 * to see if the config is dirty so that I can reload a repository model.
	 * If I use the stock JGit method to get the config it already reloads the
	 * config.  If the config changes are made within Gitblit this is fine as
	 * the returned config will still be flagged as dirty.  BUT... if the config
	 * is manipulated outside Gitblit then it fails to recognize this as dirty.
	 *  
	 * @param r
	 * @return a config
	 */
	private StoredConfig getRepositoryConfig(Repository r) {
		try {
			Field f = r.getClass().getDeclaredField("repoConfig");
			f.setAccessible(true);
			StoredConfig config = (StoredConfig) f.get(r);
			return config;
		} catch (Exception e) {
			logger.error("Failed to retrieve \"repoConfig\" via reflection", e);
		}
		return r.getConfig();
	}
	
	/**
	 * Create a repository model from the configuration and repository data.
	 * 
	 * @param repositoryName
	 * @return a repositoryModel or null if the repository does not exist
	 */
	private RepositoryModel loadRepositoryModel(String repositoryName) {
		Repository r = getRepository(repositoryName);
		if (r == null) {
			return null;
		}
		RepositoryModel model = new RepositoryModel();
		model.isBare = r.isBare();
		File basePath = getFileOrFolder(Keys.git.repositoriesFolder, "git");
		if (model.isBare) {
			model.name = com.gitblit.utils.FileUtils.getRelativePath(basePath, r.getDirectory());
		} else {
			model.name = com.gitblit.utils.FileUtils.getRelativePath(basePath, r.getDirectory().getParentFile());
		}
		model.hasCommits = JGitUtils.hasCommits(r);
		model.lastChange = JGitUtils.getLastChange(r);
		model.projectPath = StringUtils.getFirstPathElement(repositoryName);
		
		StoredConfig config = r.getConfig();
		boolean hasOrigin = !StringUtils.isEmpty(config.getString("remote", "origin", "url"));
		
		if (config != null) {
			model.description = getConfig(config, "description", "");
			model.owner = getConfig(config, "owner", "");
			model.useTickets = getConfig(config, "useTickets", false);
			model.useDocs = getConfig(config, "useDocs", false);
			model.allowForks = getConfig(config, "allowForks", true);
			model.accessRestriction = AccessRestrictionType.fromName(getConfig(config,
					"accessRestriction", settings.getString(Keys.git.defaultAccessRestriction, null)));
			model.authorizationControl = AuthorizationControl.fromName(getConfig(config,
					"authorizationControl", settings.getString(Keys.git.defaultAuthorizationControl, null)));
			model.verifyCommitter = getConfig(config, "verifyCommitter", false);
			model.showRemoteBranches = getConfig(config, "showRemoteBranches", hasOrigin);
			model.isFrozen = getConfig(config, "isFrozen", false);
			model.showReadme = getConfig(config, "showReadme", false);
			model.skipSizeCalculation = getConfig(config, "skipSizeCalculation", false);
			model.skipSummaryMetrics = getConfig(config, "skipSummaryMetrics", false);
			model.federationStrategy = FederationStrategy.fromName(getConfig(config,
					"federationStrategy", null));
			model.federationSets = new ArrayList<String>(Arrays.asList(config.getStringList(
					Constants.CONFIG_GITBLIT, null, "federationSets")));
			model.isFederated = getConfig(config, "isFederated", false);
			model.gcThreshold = getConfig(config, "gcThreshold", settings.getString(Keys.git.defaultGarbageCollectionThreshold, "500KB"));
			model.gcPeriod = getConfig(config, "gcPeriod", settings.getInteger(Keys.git.defaultGarbageCollectionPeriod, 7));
			try {
				model.lastGC = new SimpleDateFormat(Constants.ISO8601).parse(getConfig(config, "lastGC", "1970-01-01'T'00:00:00Z"));
			} catch (Exception e) {
				model.lastGC = new Date(0);
			}
			model.origin = config.getString("remote", "origin", "url");
			if (model.origin != null) {
				model.origin = model.origin.replace('\\', '/');
			}
			model.preReceiveScripts = new ArrayList<String>(Arrays.asList(config.getStringList(
					Constants.CONFIG_GITBLIT, null, "preReceiveScript")));
			model.postReceiveScripts = new ArrayList<String>(Arrays.asList(config.getStringList(
					Constants.CONFIG_GITBLIT, null, "postReceiveScript")));
			model.mailingLists = new ArrayList<String>(Arrays.asList(config.getStringList(
					Constants.CONFIG_GITBLIT, null, "mailingList")));
			model.indexedBranches = new ArrayList<String>(Arrays.asList(config.getStringList(
					Constants.CONFIG_GITBLIT, null, "indexBranch")));
			
			// Custom defined properties
			model.customFields = new LinkedHashMap<String, String>();
			for (String aProperty : config.getNames(Constants.CONFIG_GITBLIT, Constants.CONFIG_CUSTOM_FIELDS)) {
				model.customFields.put(aProperty, config.getString(Constants.CONFIG_GITBLIT, Constants.CONFIG_CUSTOM_FIELDS, aProperty));
			}
		}
		model.HEAD = JGitUtils.getHEADRef(r);
		model.availableRefs = JGitUtils.getAvailableHeadTargets(r);
		r.close();
		
		if (model.origin != null && model.origin.startsWith("file://")) {
			// repository was cloned locally... perhaps as a fork
			try {
				File folder = new File(new URI(model.origin));
				String originRepo = com.gitblit.utils.FileUtils.getRelativePath(getRepositoriesFolder(), folder);
				if (!StringUtils.isEmpty(originRepo)) {
					// ensure origin still exists
					File repoFolder = new File(getRepositoriesFolder(), originRepo);
					if (repoFolder.exists()) {
						model.originRepository = originRepo.toLowerCase();
					}
				}
			} catch (URISyntaxException e) {
				logger.error("Failed to determine fork for " + model, e);
			}
		}
		return model;
	}
	
	/**
	 * Determines if this server has the requested repository.
	 * 
	 * @param name
	 * @return true if the repository exists
	 */
	public boolean hasRepository(String repositoryName) {
		if (settings.getBoolean(Keys.git.cacheRepositoryList, true)) {
			// if we are caching use the cache to determine availability
			// otherwise we end up adding a phantom repository to the cache
			return repositoryListCache.containsKey(repositoryName.toLowerCase());
		}		
		Repository r = getRepository(repositoryName, false);
		if (r == null) {
			return false;
		}
		r.close();
		return true;
	}
	
	/**
	 * Determines if the specified user has a fork of the specified origin
	 * repository.
	 * 
	 * @param username
	 * @param origin
	 * @return true the if the user has a fork
	 */
	public boolean hasFork(String username, String origin) {
		return getFork(username, origin) != null;
	}
	
	/**
	 * Gets the name of a user's fork of the specified origin
	 * repository.
	 * 
	 * @param username
	 * @param origin
	 * @return the name of the user's fork, null otherwise
	 */
	public String getFork(String username, String origin) {
		String userProject = "~" + username.toLowerCase();
		if (settings.getBoolean(Keys.git.cacheRepositoryList, true)) {
			String userPath = userProject + "/";

			// collect all origin nodes in fork network
			Set<String> roots = new HashSet<String>();
			roots.add(origin);
			RepositoryModel originModel = repositoryListCache.get(origin);
			while (originModel != null) {
				if (!ArrayUtils.isEmpty(originModel.forks)) {
					for (String fork : originModel.forks) {
						if (!fork.startsWith(userPath)) {
							roots.add(fork);
						}
					}
				}
				
				if (originModel.originRepository != null) {
					roots.add(originModel.originRepository);
					originModel = repositoryListCache.get(originModel.originRepository);
				} else {
					// break
					originModel = null;
				}
			}
			
			for (String repository : repositoryListCache.keySet()) {
				if (repository.startsWith(userPath)) {
					RepositoryModel model = repositoryListCache.get(repository);
					if (!StringUtils.isEmpty(model.originRepository)) {
						if (roots.contains(model.originRepository)) {
							// user has a fork in this graph
							return model.name;
						}
					}
				}
			}
		} else {
			// not caching
			ProjectModel project = getProjectModel(userProject);
			for (String repository : project.repositories) {
				if (repository.startsWith(userProject)) {
					RepositoryModel model = repositoryListCache.get(repository);
					if (model.originRepository.equalsIgnoreCase(origin)) {
						// user has a fork
						return model.name;
					}
				}
			}
		}
		// user does not have a fork
		return null;
	}
	
	/**
	 * Returns the fork network for a repository by traversing up the fork graph
	 * to discover the root and then down through all children of the root node.
	 * 
	 * @param repository
	 * @return a ForkModel
	 */
	public ForkModel getForkNetwork(String repository) {
		if (settings.getBoolean(Keys.git.cacheRepositoryList, true)) {
			// find the root
			RepositoryModel model = repositoryListCache.get(repository.toLowerCase());
			while (model.originRepository != null) {
				model = repositoryListCache.get(model.originRepository);
			}
			ForkModel root = getForkModel(model.name);
			return root;
		}
		return null;
	}
	
	private ForkModel getForkModel(String repository) {
		RepositoryModel model = repositoryListCache.get(repository.toLowerCase());
		ForkModel fork = new ForkModel(model);
		if (!ArrayUtils.isEmpty(model.forks)) {
			for (String aFork : model.forks) {
				ForkModel fm = getForkModel(aFork);
				fork.forks.add(fm);
			}
		}
		return fork;
	}

	/**
	 * Returns the size in bytes of the repository. Gitblit caches the
	 * repository sizes to reduce the performance penalty of recursive
	 * calculation. The cache is updated if the repository has been changed
	 * since the last calculation.
	 * 
	 * @param model
	 * @return size in bytes
	 */
	public long calculateSize(RepositoryModel model) {
		if (repositorySizeCache.hasCurrent(model.name, model.lastChange)) {
			return repositorySizeCache.getObject(model.name);
		}
		File gitDir = FileKey.resolve(new File(repositoriesFolder, model.name), FS.DETECTED);
		long size = com.gitblit.utils.FileUtils.folderSize(gitDir);
		repositorySizeCache.updateObject(model.name, model.lastChange, size);
		return size;
	}

	/**
	 * Ensure that a cached repository is completely closed and its resources
	 * are properly released.
	 * 
	 * @param repositoryName
	 */
	private void closeRepository(String repositoryName) {
		Repository repository = getRepository(repositoryName);
		if (repository == null) {
			return;
		}
		RepositoryCache.close(repository);

		// assume 2 uses in case reflection fails
		int uses = 2;
		try {
			// The FileResolver caches repositories which is very useful
			// for performance until you want to delete a repository.
			// I have to use reflection to call close() the correct
			// number of times to ensure that the object and ref databases
			// are properly closed before I can delete the repository from
			// the filesystem.
			Field useCnt = Repository.class.getDeclaredField("useCnt");
			useCnt.setAccessible(true);
			uses = ((AtomicInteger) useCnt.get(repository)).get();
		} catch (Exception e) {
			logger.warn(MessageFormat
					.format("Failed to reflectively determine use count for repository {0}",
							repositoryName), e);
		}
		if (uses > 0) {
			logger.info(MessageFormat
					.format("{0}.useCnt={1}, calling close() {2} time(s) to close object and ref databases",
							repositoryName, uses, uses));
			for (int i = 0; i < uses; i++) {
				repository.close();
			}
		}
		
		// close any open index writer/searcher in the Lucene executor
		luceneExecutor.close(repositoryName);
	}

	/**
	 * Returns the metrics for the default branch of the specified repository.
	 * This method builds a metrics cache. The cache is updated if the
	 * repository is updated. A new copy of the metrics list is returned on each
	 * call so that modifications to the list are non-destructive.
	 * 
	 * @param model
	 * @param repository
	 * @return a new array list of metrics
	 */
	public List<Metric> getRepositoryDefaultMetrics(RepositoryModel model, Repository repository) {
		if (repositoryMetricsCache.hasCurrent(model.name, model.lastChange)) {
			return new ArrayList<Metric>(repositoryMetricsCache.getObject(model.name));
		}
		List<Metric> metrics = MetricUtils.getDateMetrics(repository, null, true, null, getTimezone());
		repositoryMetricsCache.updateObject(model.name, model.lastChange, metrics);
		return new ArrayList<Metric>(metrics);
	}

	/**
	 * Returns the gitblit string value for the specified key. If key is not
	 * set, returns defaultValue.
	 * 
	 * @param config
	 * @param field
	 * @param defaultValue
	 * @return field value or defaultValue
	 */
	private String getConfig(StoredConfig config, String field, String defaultValue) {
		String value = config.getString(Constants.CONFIG_GITBLIT, null, field);
		if (StringUtils.isEmpty(value)) {
			return defaultValue;
		}
		return value;
	}

	/**
	 * Returns the gitblit boolean value for the specified key. If key is not
	 * set, returns defaultValue.
	 * 
	 * @param config
	 * @param field
	 * @param defaultValue
	 * @return field value or defaultValue
	 */
	private boolean getConfig(StoredConfig config, String field, boolean defaultValue) {
		return config.getBoolean(Constants.CONFIG_GITBLIT, field, defaultValue);
	}
	
	/**
	 * Returns the gitblit string value for the specified key. If key is not
	 * set, returns defaultValue.
	 * 
	 * @param config
	 * @param field
	 * @param defaultValue
	 * @return field value or defaultValue
	 */
	private int getConfig(StoredConfig config, String field, int defaultValue) {
		String value = config.getString(Constants.CONFIG_GITBLIT, null, field);
		if (StringUtils.isEmpty(value)) {
			return defaultValue;
		}
		try {
			return Integer.parseInt(value);
		} catch (Exception e) {
		}
		return defaultValue;
	}

	/**
	 * Creates/updates the repository model keyed by reopsitoryName. Saves all
	 * repository settings in .git/config. This method allows for renaming
	 * repositories and will update user access permissions accordingly.
	 * 
	 * All repositories created by this method are bare and automatically have
	 * .git appended to their names, which is the standard convention for bare
	 * repositories.
	 * 
	 * @param repositoryName
	 * @param repository
	 * @param isCreate
	 * @throws GitBlitException
	 */
	public void updateRepositoryModel(String repositoryName, RepositoryModel repository,
			boolean isCreate) throws GitBlitException {
		if (gcExecutor.isCollectingGarbage(repositoryName)) {
			throw new GitBlitException(MessageFormat.format("sorry, Gitblit is busy collecting garbage in {0}",
					repositoryName));
		}
		Repository r = null;
		String projectPath = StringUtils.getFirstPathElement(repository.name);
		if (!StringUtils.isEmpty(projectPath)) {
			if (projectPath.equalsIgnoreCase(getString(Keys.web.repositoryRootGroupName, "main"))) {
				// strip leading group name
				repository.name = repository.name.substring(projectPath.length() + 1);
			}
		}
		if (isCreate) {
			// ensure created repository name ends with .git
			if (!repository.name.toLowerCase().endsWith(org.eclipse.jgit.lib.Constants.DOT_GIT_EXT)) {
				repository.name += org.eclipse.jgit.lib.Constants.DOT_GIT_EXT;
			}
			if (new File(repositoriesFolder, repository.name).exists()) {
				throw new GitBlitException(MessageFormat.format(
						"Can not create repository ''{0}'' because it already exists.",
						repository.name));
			}
			// create repository
			logger.info("create repository " + repository.name);
			r = JGitUtils.createRepository(repositoriesFolder, repository.name);
		} else {
			// rename repository
			if (!repositoryName.equalsIgnoreCase(repository.name)) {
				if (!repository.name.toLowerCase().endsWith(
						org.eclipse.jgit.lib.Constants.DOT_GIT_EXT)) {
					repository.name += org.eclipse.jgit.lib.Constants.DOT_GIT_EXT;
				}
				if (new File(repositoriesFolder, repository.name).exists()) {
					throw new GitBlitException(MessageFormat.format(
							"Failed to rename ''{0}'' because ''{1}'' already exists.",
							repositoryName, repository.name));
				}
				closeRepository(repositoryName);
				File folder = new File(repositoriesFolder, repositoryName);
				File destFolder = new File(repositoriesFolder, repository.name);
				if (destFolder.exists()) {
					throw new GitBlitException(
							MessageFormat
									.format("Can not rename repository ''{0}'' to ''{1}'' because ''{1}'' already exists.",
											repositoryName, repository.name));
				}
				File parentFile = destFolder.getParentFile();
				if (!parentFile.exists() && !parentFile.mkdirs()) {
					throw new GitBlitException(MessageFormat.format(
							"Failed to create folder ''{0}''", parentFile.getAbsolutePath()));
				}
				if (!folder.renameTo(destFolder)) {
					throw new GitBlitException(MessageFormat.format(
							"Failed to rename repository ''{0}'' to ''{1}''.", repositoryName,
							repository.name));
				}
				// rename the roles
				if (!userService.renameRepositoryRole(repositoryName, repository.name)) {
					throw new GitBlitException(MessageFormat.format(
							"Failed to rename repository permissions ''{0}'' to ''{1}''.",
							repositoryName, repository.name));
				}
				
				// rename fork origins in their configs
				if (!ArrayUtils.isEmpty(repository.forks)) {
					for (String fork : repository.forks) {
						Repository rf = getRepository(fork);
						try {
							StoredConfig config = rf.getConfig();
							String origin = config.getString("remote", "origin", "url");
							origin = origin.replace(repositoryName, repository.name);
							config.setString("remote", "origin", "url", origin);
							config.save();
						} catch (Exception e) {
							logger.error("Failed to update repository fork config for " + fork, e);
						}
						rf.close();
					}
				}
				
				// remove this repository from any origin model's fork list
				if (!StringUtils.isEmpty(repository.originRepository)) {
					RepositoryModel origin = repositoryListCache.get(repository.originRepository);
					if (origin != null && !ArrayUtils.isEmpty(origin.forks)) {
						origin.forks.remove(repositoryName);
					}
				}

				// clear the cache
				clearRepositoryMetadataCache(repositoryName);
				repository.resetDisplayName();
			}

			// load repository
			logger.info("edit repository " + repository.name);
			r = getRepository(repository.name);
		}

		// update settings
		if (r != null) {
			updateConfiguration(r, repository);
			// only update symbolic head if it changes
			String currentRef = JGitUtils.getHEADRef(r);
			if (!StringUtils.isEmpty(repository.HEAD) && !repository.HEAD.equals(currentRef)) {
				logger.info(MessageFormat.format("Relinking {0} HEAD from {1} to {2}", 
						repository.name, currentRef, repository.HEAD));
				if (JGitUtils.setHEADtoRef(r, repository.HEAD)) {
					// clear the cache
					clearRepositoryMetadataCache(repository.name);
				}
			}

			// close the repository object
			r.close();
		}
		
		// update repository cache
		removeFromCachedRepositoryList(repositoryName);
		// model will actually be replaced on next load because config is stale
		addToCachedRepositoryList(repository);
	}
	
	/**
	 * Updates the Gitblit configuration for the specified repository.
	 * 
	 * @param r
	 *            the Git repository
	 * @param repository
	 *            the Gitblit repository model
	 */
	public void updateConfiguration(Repository r, RepositoryModel repository) {
		StoredConfig config = r.getConfig();
		config.setString(Constants.CONFIG_GITBLIT, null, "description", repository.description);
		config.setString(Constants.CONFIG_GITBLIT, null, "owner", repository.owner);
		config.setBoolean(Constants.CONFIG_GITBLIT, null, "useTickets", repository.useTickets);
		config.setBoolean(Constants.CONFIG_GITBLIT, null, "useDocs", repository.useDocs);
		config.setBoolean(Constants.CONFIG_GITBLIT, null, "allowForks", repository.allowForks);
		config.setString(Constants.CONFIG_GITBLIT, null, "accessRestriction", repository.accessRestriction.name());
		config.setString(Constants.CONFIG_GITBLIT, null, "authorizationControl", repository.authorizationControl.name());
		config.setBoolean(Constants.CONFIG_GITBLIT, null, "verifyCommitter", repository.verifyCommitter);
		config.setBoolean(Constants.CONFIG_GITBLIT, null, "showRemoteBranches", repository.showRemoteBranches);
		config.setBoolean(Constants.CONFIG_GITBLIT, null, "isFrozen", repository.isFrozen);
		config.setBoolean(Constants.CONFIG_GITBLIT, null, "showReadme", repository.showReadme);
		config.setBoolean(Constants.CONFIG_GITBLIT, null, "skipSizeCalculation", repository.skipSizeCalculation);
		config.setBoolean(Constants.CONFIG_GITBLIT, null, "skipSummaryMetrics", repository.skipSummaryMetrics);
		config.setString(Constants.CONFIG_GITBLIT, null, "federationStrategy",
				repository.federationStrategy.name());
		config.setBoolean(Constants.CONFIG_GITBLIT, null, "isFederated", repository.isFederated);
		config.setString(Constants.CONFIG_GITBLIT, null, "gcThreshold", repository.gcThreshold);
		config.setInt(Constants.CONFIG_GITBLIT, null, "gcPeriod", repository.gcPeriod);
		if (repository.lastGC != null) {
			config.setString(Constants.CONFIG_GITBLIT, null, "lastGC", new SimpleDateFormat(Constants.ISO8601).format(repository.lastGC));
		}

		updateList(config, "federationSets", repository.federationSets);
		updateList(config, "preReceiveScript", repository.preReceiveScripts);
		updateList(config, "postReceiveScript", repository.postReceiveScripts);
		updateList(config, "mailingList", repository.mailingLists);
		updateList(config, "indexBranch", repository.indexedBranches);
		
		// User Defined Properties
		if (repository.customFields != null) {
			if (repository.customFields.size() == 0) {
				// clear section
				config.unsetSection(Constants.CONFIG_GITBLIT, Constants.CONFIG_CUSTOM_FIELDS);
			} else {
				for (Entry<String, String> property : repository.customFields.entrySet()) {
					// set field
					String key = property.getKey();
					String value = property.getValue();
					config.setString(Constants.CONFIG_GITBLIT, Constants.CONFIG_CUSTOM_FIELDS, key, value);
				}
			}
		}

		try {
			config.save();
		} catch (IOException e) {
			logger.error("Failed to save repository config!", e);
		}
	}
	
	private void updateList(StoredConfig config, String field, List<String> list) {
		// a null list is skipped, not cleared
		// this is for RPC administration where an older manager might be used
		if (list == null) {
			return;
		}
		if (ArrayUtils.isEmpty(list)) {
			config.unset(Constants.CONFIG_GITBLIT, null, field);
		} else {
			config.setStringList(Constants.CONFIG_GITBLIT, null, field, list);
		}
	}

	/**
	 * Deletes the repository from the file system and removes the repository
	 * permission from all repository users.
	 * 
	 * @param model
	 * @return true if successful
	 */
	public boolean deleteRepositoryModel(RepositoryModel model) {
		return deleteRepository(model.name);
	}

	/**
	 * Deletes the repository from the file system and removes the repository
	 * permission from all repository users.
	 * 
	 * @param repositoryName
	 * @return true if successful
	 */
	public boolean deleteRepository(String repositoryName) {
		try {
			closeRepository(repositoryName);
			// clear the repository cache
			clearRepositoryMetadataCache(repositoryName);
			
			RepositoryModel model = removeFromCachedRepositoryList(repositoryName);
			if (model != null && !ArrayUtils.isEmpty(model.forks)) {
				resetRepositoryListCache();
			}

			File folder = new File(repositoriesFolder, repositoryName);
			if (folder.exists() && folder.isDirectory()) {
				FileUtils.delete(folder, FileUtils.RECURSIVE | FileUtils.RETRY);
				if (userService.deleteRepositoryRole(repositoryName)) {
					logger.info(MessageFormat.format("Repository \"{0}\" deleted", repositoryName));
					return true;
				}
			}
		} catch (Throwable t) {
			logger.error(MessageFormat.format("Failed to delete repository {0}", repositoryName), t);
		}
		return false;
	}

	/**
	 * Returns an html version of the commit message with any global or
	 * repository-specific regular expression substitution applied.
	 * 
	 * @param repositoryName
	 * @param text
	 * @return html version of the commit message
	 */
	public String processCommitMessage(String repositoryName, String text) {
		String html = StringUtils.breakLinesForHtml(text);
		Map<String, String> map = new HashMap<String, String>();
		// global regex keys
		if (settings.getBoolean(Keys.regex.global, false)) {
			for (String key : settings.getAllKeys(Keys.regex.global)) {
				if (!key.equals(Keys.regex.global)) {
					String subKey = key.substring(key.lastIndexOf('.') + 1);
					map.put(subKey, settings.getString(key, ""));
				}
			}
		}

		// repository-specific regex keys
		List<String> keys = settings.getAllKeys(Keys.regex._ROOT + "."
				+ repositoryName.toLowerCase());
		for (String key : keys) {
			String subKey = key.substring(key.lastIndexOf('.') + 1);
			map.put(subKey, settings.getString(key, ""));
		}

		for (Entry<String, String> entry : map.entrySet()) {
			String definition = entry.getValue().trim();
			String[] chunks = definition.split("!!!");
			if (chunks.length == 2) {
				html = html.replaceAll(chunks[0], chunks[1]);
			} else {
				logger.warn(entry.getKey()
						+ " improperly formatted.  Use !!! to separate match from replacement: "
						+ definition);
			}
		}
		return html;
	}

	/**
	 * Returns Gitblit's scheduled executor service for scheduling tasks.
	 * 
	 * @return scheduledExecutor
	 */
	public ScheduledExecutorService executor() {
		return scheduledExecutor;
	}

	public static boolean canFederate() {
		String passphrase = getString(Keys.federation.passphrase, "");
		return !StringUtils.isEmpty(passphrase);
	}

	/**
	 * Configures this Gitblit instance to pull any registered federated gitblit
	 * instances.
	 */
	private void configureFederation() {
		boolean validPassphrase = true;
		String passphrase = settings.getString(Keys.federation.passphrase, "");
		if (StringUtils.isEmpty(passphrase)) {
			logger.warn("Federation passphrase is blank! This server can not be PULLED from.");
			validPassphrase = false;
		}
		if (validPassphrase) {
			// standard tokens
			for (FederationToken tokenType : FederationToken.values()) {
				logger.info(MessageFormat.format("Federation {0} token = {1}", tokenType.name(),
						getFederationToken(tokenType)));
			}

			// federation set tokens
			for (String set : settings.getStrings(Keys.federation.sets)) {
				logger.info(MessageFormat.format("Federation Set {0} token = {1}", set,
						getFederationToken(set)));
			}
		}

		// Schedule the federation executor
		List<FederationModel> registrations = getFederationRegistrations();
		if (registrations.size() > 0) {
			FederationPullExecutor executor = new FederationPullExecutor(registrations, true);
			scheduledExecutor.schedule(executor, 1, TimeUnit.MINUTES);
		}
	}

	/**
	 * Returns the list of federated gitblit instances that this instance will
	 * try to pull.
	 * 
	 * @return list of registered gitblit instances
	 */
	public List<FederationModel> getFederationRegistrations() {
		if (federationRegistrations.isEmpty()) {
			federationRegistrations.addAll(FederationUtils.getFederationRegistrations(settings));
		}
		return federationRegistrations;
	}

	/**
	 * Retrieve the specified federation registration.
	 * 
	 * @param name
	 *            the name of the registration
	 * @return a federation registration
	 */
	public FederationModel getFederationRegistration(String url, String name) {
		// check registrations
		for (FederationModel r : getFederationRegistrations()) {
			if (r.name.equals(name) && r.url.equals(url)) {
				return r;
			}
		}

		// check the results
		for (FederationModel r : getFederationResultRegistrations()) {
			if (r.name.equals(name) && r.url.equals(url)) {
				return r;
			}
		}
		return null;
	}

	/**
	 * Returns the list of federation sets.
	 * 
	 * @return list of federation sets
	 */
	public List<FederationSet> getFederationSets(String gitblitUrl) {
		List<FederationSet> list = new ArrayList<FederationSet>();
		// generate standard tokens
		for (FederationToken type : FederationToken.values()) {
			FederationSet fset = new FederationSet(type.toString(), type, getFederationToken(type));
			fset.repositories = getRepositories(gitblitUrl, fset.token);
			list.add(fset);
		}
		// generate tokens for federation sets
		for (String set : settings.getStrings(Keys.federation.sets)) {
			FederationSet fset = new FederationSet(set, FederationToken.REPOSITORIES,
					getFederationToken(set));
			fset.repositories = getRepositories(gitblitUrl, fset.token);
			list.add(fset);
		}
		return list;
	}

	/**
	 * Returns the list of possible federation tokens for this Gitblit instance.
	 * 
	 * @return list of federation tokens
	 */
	public List<String> getFederationTokens() {
		List<String> tokens = new ArrayList<String>();
		// generate standard tokens
		for (FederationToken type : FederationToken.values()) {
			tokens.add(getFederationToken(type));
		}
		// generate tokens for federation sets
		for (String set : settings.getStrings(Keys.federation.sets)) {
			tokens.add(getFederationToken(set));
		}
		return tokens;
	}

	/**
	 * Returns the specified federation token for this Gitblit instance.
	 * 
	 * @param type
	 * @return a federation token
	 */
	public String getFederationToken(FederationToken type) {
		return getFederationToken(type.name());
	}

	/**
	 * Returns the specified federation token for this Gitblit instance.
	 * 
	 * @param value
	 * @return a federation token
	 */
	public String getFederationToken(String value) {
		String passphrase = settings.getString(Keys.federation.passphrase, "");
		return StringUtils.getSHA1(passphrase + "-" + value);
	}

	/**
	 * Compares the provided token with this Gitblit instance's tokens and
	 * determines if the requested permission may be granted to the token.
	 * 
	 * @param req
	 * @param token
	 * @return true if the request can be executed
	 */
	public boolean validateFederationRequest(FederationRequest req, String token) {
		String all = getFederationToken(FederationToken.ALL);
		String unr = getFederationToken(FederationToken.USERS_AND_REPOSITORIES);
		String jur = getFederationToken(FederationToken.REPOSITORIES);
		switch (req) {
		case PULL_REPOSITORIES:
			return token.equals(all) || token.equals(unr) || token.equals(jur);
		case PULL_USERS:
		case PULL_TEAMS:
			return token.equals(all) || token.equals(unr);
		case PULL_SETTINGS:
		case PULL_SCRIPTS:
			return token.equals(all);
		default:
			break;
		}
		return false;
	}

	/**
	 * Acknowledge and cache the status of a remote Gitblit instance.
	 * 
	 * @param identification
	 *            the identification of the pulling Gitblit instance
	 * @param registration
	 *            the registration from the pulling Gitblit instance
	 * @return true if acknowledged
	 */
	public boolean acknowledgeFederationStatus(String identification, FederationModel registration) {
		// reset the url to the identification of the pulling Gitblit instance
		registration.url = identification;
		String id = identification;
		if (!StringUtils.isEmpty(registration.folder)) {
			id += "-" + registration.folder;
		}
		federationPullResults.put(id, registration);
		return true;
	}

	/**
	 * Returns the list of registration results.
	 * 
	 * @return the list of registration results
	 */
	public List<FederationModel> getFederationResultRegistrations() {
		return new ArrayList<FederationModel>(federationPullResults.values());
	}

	/**
	 * Submit a federation proposal. The proposal is cached locally and the
	 * Gitblit administrator(s) are notified via email.
	 * 
	 * @param proposal
	 *            the proposal
	 * @param gitblitUrl
	 *            the url of your gitblit instance to send an email to
	 *            administrators
	 * @return true if the proposal was submitted
	 */
	public boolean submitFederationProposal(FederationProposal proposal, String gitblitUrl) {
		// convert proposal to json
		String json = JsonUtils.toJsonString(proposal);

		try {
			// make the proposals folder
			File proposalsFolder = getProposalsFolder();
			proposalsFolder.mkdirs();

			// cache json to a file
			File file = new File(proposalsFolder, proposal.token + Constants.PROPOSAL_EXT);
			com.gitblit.utils.FileUtils.writeContent(file, json);
		} catch (Exception e) {
			logger.error(MessageFormat.format("Failed to cache proposal from {0}", proposal.url), e);
		}

		// send an email, if possible
		try {
			Message message = mailExecutor.createMessageForAdministrators();
			if (message != null) {
				message.setSubject("Federation proposal from " + proposal.url);
				message.setText("Please review the proposal @ " + gitblitUrl + "/proposal/"
						+ proposal.token);
				mailExecutor.queue(message);
			}
		} catch (Throwable t) {
			logger.error("Failed to notify administrators of proposal", t);
		}
		return true;
	}

	/**
	 * Returns the list of pending federation proposals
	 * 
	 * @return list of federation proposals
	 */
	public List<FederationProposal> getPendingFederationProposals() {
		List<FederationProposal> list = new ArrayList<FederationProposal>();
		File folder = getProposalsFolder();
		if (folder.exists()) {
			File[] files = folder.listFiles(new FileFilter() {
				@Override
				public boolean accept(File file) {
					return file.isFile()
							&& file.getName().toLowerCase().endsWith(Constants.PROPOSAL_EXT);
				}
			});
			for (File file : files) {
				String json = com.gitblit.utils.FileUtils.readContent(file, null);
				FederationProposal proposal = JsonUtils.fromJsonString(json,
						FederationProposal.class);
				list.add(proposal);
			}
		}
		return list;
	}

	/**
	 * Get repositories for the specified token.
	 * 
	 * @param gitblitUrl
	 *            the base url of this gitblit instance
	 * @param token
	 *            the federation token
	 * @return a map of <cloneurl, RepositoryModel>
	 */
	public Map<String, RepositoryModel> getRepositories(String gitblitUrl, String token) {
		Map<String, String> federationSets = new HashMap<String, String>();
		for (String set : getStrings(Keys.federation.sets)) {
			federationSets.put(getFederationToken(set), set);
		}

		// Determine the Gitblit clone url
		StringBuilder sb = new StringBuilder();
		sb.append(gitblitUrl);
		sb.append(Constants.GIT_PATH);
		sb.append("{0}");
		String cloneUrl = sb.toString();

		// Retrieve all available repositories
		UserModel user = new UserModel(Constants.FEDERATION_USER);
		user.canAdmin = true;
		List<RepositoryModel> list = getRepositoryModels(user);

		// create the [cloneurl, repositoryModel] map
		Map<String, RepositoryModel> repositories = new HashMap<String, RepositoryModel>();
		for (RepositoryModel model : list) {
			// by default, setup the url for THIS repository
			String url = MessageFormat.format(cloneUrl, model.name);
			switch (model.federationStrategy) {
			case EXCLUDE:
				// skip this repository
				continue;
			case FEDERATE_ORIGIN:
				// federate the origin, if it is defined
				if (!StringUtils.isEmpty(model.origin)) {
					url = model.origin;
				}
				break;
			default:
				break;
			}

			if (federationSets.containsKey(token)) {
				// include repositories only for federation set
				String set = federationSets.get(token);
				if (model.federationSets.contains(set)) {
					repositories.put(url, model);
				}
			} else {
				// standard federation token for ALL
				repositories.put(url, model);
			}
		}
		return repositories;
	}

	/**
	 * Creates a proposal from the token.
	 * 
	 * @param gitblitUrl
	 *            the url of this Gitblit instance
	 * @param token
	 * @return a potential proposal
	 */
	public FederationProposal createFederationProposal(String gitblitUrl, String token) {
		FederationToken tokenType = FederationToken.REPOSITORIES;
		for (FederationToken type : FederationToken.values()) {
			if (token.equals(getFederationToken(type))) {
				tokenType = type;
				break;
			}
		}
		Map<String, RepositoryModel> repositories = getRepositories(gitblitUrl, token);
		FederationProposal proposal = new FederationProposal(gitblitUrl, tokenType, token,
				repositories);
		return proposal;
	}

	/**
	 * Returns the proposal identified by the supplied token.
	 * 
	 * @param token
	 * @return the specified proposal or null
	 */
	public FederationProposal getPendingFederationProposal(String token) {
		List<FederationProposal> list = getPendingFederationProposals();
		for (FederationProposal proposal : list) {
			if (proposal.token.equals(token)) {
				return proposal;
			}
		}
		return null;
	}

	/**
	 * Deletes a pending federation proposal.
	 * 
	 * @param a
	 *            proposal
	 * @return true if the proposal was deleted
	 */
	public boolean deletePendingFederationProposal(FederationProposal proposal) {
		File folder = getProposalsFolder();
		File file = new File(folder, proposal.token + Constants.PROPOSAL_EXT);
		return file.delete();
	}

	/**
	 * Returns the list of all Groovy push hook scripts. Script files must have
	 * .groovy extension
	 * 
	 * @return list of available hook scripts
	 */
	public List<String> getAllScripts() {
		File groovyFolder = getGroovyScriptsFolder();
		File[] files = groovyFolder.listFiles(new FileFilter() {
			@Override
			public boolean accept(File pathname) {
				return pathname.isFile() && pathname.getName().endsWith(".groovy");
			}
		});
		List<String> scripts = new ArrayList<String>();
		if (files != null) {
			for (File file : files) {
				String script = file.getName().substring(0, file.getName().lastIndexOf('.'));
				scripts.add(script);
			}
		}
		return scripts;
	}

	/**
	 * Returns the list of pre-receive scripts the repository inherited from the
	 * global settings and team affiliations.
	 * 
	 * @param repository
	 *            if null only the globally specified scripts are returned
	 * @return a list of scripts
	 */
	public List<String> getPreReceiveScriptsInherited(RepositoryModel repository) {
		Set<String> scripts = new LinkedHashSet<String>();
		// Globals
		for (String script : getStrings(Keys.groovy.preReceiveScripts)) {
			if (script.endsWith(".groovy")) {
				scripts.add(script.substring(0, script.lastIndexOf('.')));
			} else {
				scripts.add(script);
			}
		}

		// Team Scripts
		if (repository != null) {
			for (String teamname : userService.getTeamnamesForRepositoryRole(repository.name)) {
				TeamModel team = userService.getTeamModel(teamname);
				scripts.addAll(team.preReceiveScripts);
			}
		}
		return new ArrayList<String>(scripts);
	}

	/**
	 * Returns the list of all available Groovy pre-receive push hook scripts
	 * that are not already inherited by the repository. Script files must have
	 * .groovy extension
	 * 
	 * @param repository
	 *            optional parameter
	 * @return list of available hook scripts
	 */
	public List<String> getPreReceiveScriptsUnused(RepositoryModel repository) {
		Set<String> inherited = new TreeSet<String>(getPreReceiveScriptsInherited(repository));

		// create list of available scripts by excluding inherited scripts
		List<String> scripts = new ArrayList<String>();
		for (String script : getAllScripts()) {
			if (!inherited.contains(script)) {
				scripts.add(script);
			}
		}
		return scripts;
	}

	/**
	 * Returns the list of post-receive scripts the repository inherited from
	 * the global settings and team affiliations.
	 * 
	 * @param repository
	 *            if null only the globally specified scripts are returned
	 * @return a list of scripts
	 */
	public List<String> getPostReceiveScriptsInherited(RepositoryModel repository) {
		Set<String> scripts = new LinkedHashSet<String>();
		// Global Scripts
		for (String script : getStrings(Keys.groovy.postReceiveScripts)) {
			if (script.endsWith(".groovy")) {
				scripts.add(script.substring(0, script.lastIndexOf('.')));
			} else {
				scripts.add(script);
			}
		}
		// Team Scripts
		if (repository != null) {
			for (String teamname : userService.getTeamnamesForRepositoryRole(repository.name)) {
				TeamModel team = userService.getTeamModel(teamname);
				scripts.addAll(team.postReceiveScripts);
			}
		}
		return new ArrayList<String>(scripts);
	}

	/**
	 * Returns the list of unused Groovy post-receive push hook scripts that are
	 * not already inherited by the repository. Script files must have .groovy
	 * extension
	 * 
	 * @param repository
	 *            optional parameter
	 * @return list of available hook scripts
	 */
	public List<String> getPostReceiveScriptsUnused(RepositoryModel repository) {
		Set<String> inherited = new TreeSet<String>(getPostReceiveScriptsInherited(repository));

		// create list of available scripts by excluding inherited scripts
		List<String> scripts = new ArrayList<String>();
		for (String script : getAllScripts()) {
			if (!inherited.contains(script)) {
				scripts.add(script);
			}
		}
		return scripts;
	}
	
	/**
	 * Search the specified repositories using the Lucene query.
	 * 
	 * @param query
	 * @param page
	 * @param pageSize
	 * @param repositories
	 * @return
	 */
	public List<SearchResult> search(String query, int page, int pageSize, List<String> repositories) {		
		List<SearchResult> srs = luceneExecutor.search(query, page, pageSize, repositories);
		return srs;
	}

	/**
	 * Notify the administrators by email.
	 * 
	 * @param subject
	 * @param message
	 */
	public void sendMailToAdministrators(String subject, String message) {
		try {
			Message mail = mailExecutor.createMessageForAdministrators();
			if (mail != null) {
				mail.setSubject(subject);
				mail.setText(message);
				mailExecutor.queue(mail);
			}
		} catch (MessagingException e) {
			logger.error("Messaging error", e);
		}
	}

	/**
	 * Notify users by email of something.
	 * 
	 * @param subject
	 * @param message
	 * @param toAddresses
	 */
	public void sendMail(String subject, String message, Collection<String> toAddresses) {
		this.sendMail(subject, message, toAddresses.toArray(new String[0]));
	}

	/**
	 * Notify users by email of something.
	 * 
	 * @param subject
	 * @param message
	 * @param toAddresses
	 */
	public void sendMail(String subject, String message, String... toAddresses) {
		try {
			Message mail = mailExecutor.createMessage(toAddresses);
			if (mail != null) {
				mail.setSubject(subject);
				mail.setText(message);
				mailExecutor.queue(mail);
			}
		} catch (MessagingException e) {
			logger.error("Messaging error", e);
		}
	}

	/**
	 * Notify users by email of something.
	 * 
	 * @param subject
	 * @param message
	 * @param toAddresses
	 */
	public void sendHtmlMail(String subject, String message, Collection<String> toAddresses) {
		this.sendHtmlMail(subject, message, toAddresses.toArray(new String[0]));
	}

	/**
	 * Notify users by email of something.
	 * 
	 * @param subject
	 * @param message
	 * @param toAddresses
	 */
	public void sendHtmlMail(String subject, String message, String... toAddresses) {
		try {
			Message mail = mailExecutor.createMessage(toAddresses);
			if (mail != null) {
				mail.setSubject(subject);
				mail.setContent(message, "text/html");
				mailExecutor.queue(mail);
			}
		} catch (MessagingException e) {
			logger.error("Messaging error", e);
		}
	}

	/**
	 * Returns the descriptions/comments of the Gitblit config settings.
	 * 
	 * @return SettingsModel
	 */
	public ServerSettings getSettingsModel() {
		// ensure that the current values are updated in the setting models
		for (String key : settings.getAllKeys(null)) {
			SettingModel setting = settingsModel.get(key);
			if (setting == null) {
				// unreferenced setting, create a setting model
				setting = new SettingModel();
				setting.name = key;
				settingsModel.add(setting);
			}
			setting.currentValue = settings.getString(key, "");			
		}
		settingsModel.pushScripts = getAllScripts();
		return settingsModel;
	}

	/**
	 * Parse the properties file and aggregate all the comments by the setting
	 * key. A setting model tracks the current value, the default value, the
	 * description of the setting and and directives about the setting.
	 * @param referencePropertiesInputStream
	 * 
	 * @return Map<String, SettingModel>
	 */
	private ServerSettings loadSettingModels(InputStream referencePropertiesInputStream) {
		ServerSettings settingsModel = new ServerSettings();
		settingsModel.supportsCredentialChanges = userService.supportsCredentialChanges();
		settingsModel.supportsDisplayNameChanges = userService.supportsDisplayNameChanges();
		settingsModel.supportsEmailAddressChanges = userService.supportsEmailAddressChanges();
		settingsModel.supportsTeamMembershipChanges = userService.supportsTeamMembershipChanges();
		try {
			// Read bundled Gitblit properties to extract setting descriptions.
			// This copy is pristine and only used for populating the setting
			// models map.
			InputStream is = referencePropertiesInputStream;
			BufferedReader propertiesReader = new BufferedReader(new InputStreamReader(is));
			StringBuilder description = new StringBuilder();
			SettingModel setting = new SettingModel();
			String line = null;
			while ((line = propertiesReader.readLine()) != null) {
				if (line.length() == 0) {
					description.setLength(0);
					setting = new SettingModel();
				} else {
					if (line.charAt(0) == '#') {
						if (line.length() > 1) {
							String text = line.substring(1).trim();
							if (SettingModel.CASE_SENSITIVE.equals(text)) {
								setting.caseSensitive = true;
							} else if (SettingModel.RESTART_REQUIRED.equals(text)) {
								setting.restartRequired = true;
							} else if (SettingModel.SPACE_DELIMITED.equals(text)) {
								setting.spaceDelimited = true;
							} else if (text.startsWith(SettingModel.SINCE)) {
								try {
									setting.since = text.split(" ")[1];
								} catch (Exception e) {
									setting.since = text;
								}
							} else {
								description.append(text);
								description.append('\n');
							}
						}
					} else {
						String[] kvp = line.split("=", 2);
						String key = kvp[0].trim();
						setting.name = key;
						setting.defaultValue = kvp[1].trim();
						setting.currentValue = setting.defaultValue;
						setting.description = description.toString().trim();
						settingsModel.add(setting);
						description.setLength(0);
						setting = new SettingModel();
					}
				}
			}
			propertiesReader.close();
		} catch (NullPointerException e) {
			logger.error("Failed to find resource copy of gitblit.properties");
		} catch (IOException e) {
			logger.error("Failed to load resource copy of gitblit.properties");
		}
		return settingsModel;
	}

	/**
	 * Configure the Gitblit singleton with the specified settings source. This
	 * source may be file settings (Gitblit GO) or may be web.xml settings
	 * (Gitblit WAR).
	 * 
	 * @param settings
	 */
	public void configureContext(IStoredSettings settings, boolean startFederation) {
		logger.info("Reading configuration from " + settings.toString());
		this.settings = settings;

		repositoriesFolder = getRepositoriesFolder();
		logger.info("Git repositories folder " + repositoriesFolder.getAbsolutePath());

		// prepare service executors
		mailExecutor = new MailExecutor(settings);
		luceneExecutor = new LuceneExecutor(settings, repositoriesFolder);
		gcExecutor = new GCExecutor(settings);
		
		// calculate repository list settings checksum for future config changes
		repositoryListSettingsChecksum.set(getRepositoryListSettingsChecksum());

		// build initial repository list
		if (settings.getBoolean(Keys.git.cacheRepositoryList,  true)) {
			logger.info("Identifying available repositories...");
			getRepositoryList();
		}
		
		logTimezone("JVM", TimeZone.getDefault());
		logTimezone(Constants.NAME, getTimezone());

		serverStatus = new ServerStatus(isGO());

		if (this.userService == null) {
			String realm = settings.getString(Keys.realm.userService, "users.properties");
			IUserService loginService = null;
			try {
				// check to see if this "file" is a login service class
				Class<?> realmClass = Class.forName(realm);
				loginService = (IUserService) realmClass.newInstance();
			} catch (Throwable t) {
				loginService = new GitblitUserService();
			}
			setUserService(loginService);
		}
		
		// load and cache the project metadata
		projectConfigs = new FileBasedConfig(getFileOrFolder(Keys.web.projectsFile, "projects.conf"), FS.detect());
		getProjectConfigs();
		
		// schedule mail engine
		if (mailExecutor.isReady()) {
			logger.info("Mail executor is scheduled to process the message queue every 2 minutes.");
			scheduledExecutor.scheduleAtFixedRate(mailExecutor, 1, 2, TimeUnit.MINUTES);
		} else {
			logger.warn("Mail server is not properly configured.  Mail services disabled.");
		}
		
		// schedule lucene engine
		logger.info("Lucene executor is scheduled to process indexed branches every 2 minutes.");
		scheduledExecutor.scheduleAtFixedRate(luceneExecutor, 1, 2, TimeUnit.MINUTES);
		
		// schedule gc engine
		if (gcExecutor.isReady()) {
			logger.info("GC executor is scheduled to scan repositories every 24 hours.");
			Calendar c = Calendar.getInstance();
			c.set(Calendar.HOUR_OF_DAY, settings.getInteger(Keys.git.garbageCollectionHour, 0));
			c.set(Calendar.MINUTE, 0);
			c.set(Calendar.SECOND, 0);
			c.set(Calendar.MILLISECOND, 0);
			Date cd = c.getTime();
			Date now = new Date();
			int delay = 0;
			if (cd.before(now)) {
				c.add(Calendar.DATE, 1);
				cd = c.getTime();
			}
			delay = (int) ((cd.getTime() - now.getTime())/TimeUtils.MIN);
			String when = delay + " mins";
			if (delay > 60) {
				when = MessageFormat.format("{0,number,0.0} hours", ((float)delay)/60f);
			}
			logger.info(MessageFormat.format("Next scheculed GC scan is in {0}", when));
			scheduledExecutor.scheduleAtFixedRate(gcExecutor, delay, 60*24, TimeUnit.MINUTES);
		}
		
		if (startFederation) {
			configureFederation();
		}
		
		// Configure JGit
		WindowCacheConfig cfg = new WindowCacheConfig();
		
		cfg.setPackedGitWindowSize(settings.getFilesize(Keys.git.packedGitWindowSize, cfg.getPackedGitWindowSize()));
		cfg.setPackedGitLimit(settings.getFilesize(Keys.git.packedGitLimit, cfg.getPackedGitLimit()));
		cfg.setDeltaBaseCacheLimit(settings.getFilesize(Keys.git.deltaBaseCacheLimit, cfg.getDeltaBaseCacheLimit()));
		cfg.setPackedGitOpenFiles(settings.getFilesize(Keys.git.packedGitOpenFiles, cfg.getPackedGitOpenFiles()));
		cfg.setStreamFileThreshold(settings.getFilesize(Keys.git.streamFileThreshold, cfg.getStreamFileThreshold()));
		cfg.setPackedGitMMAP(settings.getBoolean(Keys.git.packedGitMmap, cfg.isPackedGitMMAP()));
		
		try {
			WindowCache.reconfigure(cfg);
			logger.debug(MessageFormat.format("{0} = {1,number,0}", Keys.git.packedGitWindowSize, cfg.getPackedGitWindowSize()));
			logger.debug(MessageFormat.format("{0} = {1,number,0}", Keys.git.packedGitLimit, cfg.getPackedGitLimit()));
			logger.debug(MessageFormat.format("{0} = {1,number,0}", Keys.git.deltaBaseCacheLimit, cfg.getDeltaBaseCacheLimit()));
			logger.debug(MessageFormat.format("{0} = {1,number,0}", Keys.git.packedGitOpenFiles, cfg.getPackedGitOpenFiles()));
			logger.debug(MessageFormat.format("{0} = {1,number,0}", Keys.git.streamFileThreshold, cfg.getStreamFileThreshold()));
			logger.debug(MessageFormat.format("{0} = {1}", Keys.git.packedGitMmap, cfg.isPackedGitMMAP()));
		} catch (IllegalArgumentException e) {
			logger.error("Failed to configure JGit parameters!", e);
		}

		ContainerUtils.CVE_2007_0450.test();
	}
	
	private void logTimezone(String type, TimeZone zone) {
		SimpleDateFormat df = new SimpleDateFormat("z Z");
		df.setTimeZone(zone);
		String offset = df.format(new Date());
		logger.info(type + " timezone is " + zone.getID() + " (" + offset + ")");
	}

	/**
	 * Configure Gitblit from the web.xml, if no configuration has already been
	 * specified.
	 * 
	 * @see ServletContextListener.contextInitialize(ServletContextEvent)
	 */
	@Override
	public void contextInitialized(ServletContextEvent contextEvent) {
		contextInitialized(contextEvent, contextEvent.getServletContext().getResourceAsStream("/WEB-INF/reference.properties"));
	}

	public void contextInitialized(ServletContextEvent contextEvent, InputStream referencePropertiesInputStream) {
		servletContext = contextEvent.getServletContext();
		if (settings == null) {
			// Gitblit WAR is running in a servlet container
			ServletContext context = contextEvent.getServletContext();
			WebXmlSettings webxmlSettings = new WebXmlSettings(context);

			// 0.7.0 web.properties in the deployed war folder
			String webProps = context.getRealPath("/WEB-INF/web.properties");
			if (!StringUtils.isEmpty(webProps)) {
				File overrideFile = new File(webProps);
				if (overrideFile.exists()) {
					webxmlSettings.applyOverrides(overrideFile);
				}
			}
			

			// 0.8.0 gitblit.properties file located outside the deployed war
			// folder lie, for example, on RedHat OpenShift.
			File overrideFile = getFileOrFolder("gitblit.properties");
			if (!overrideFile.getPath().equals("gitblit.properties")) {
				webxmlSettings.applyOverrides(overrideFile);
			}
			configureContext(webxmlSettings, true);

			// Copy the included scripts to the configured groovy folder
			File localScripts = getFileOrFolder(Keys.groovy.scriptsFolder, "groovy");
			if (!localScripts.exists()) {
				File includedScripts = new File(context.getRealPath("/WEB-INF/groovy"));
				if (!includedScripts.equals(localScripts)) {
					try {
						com.gitblit.utils.FileUtils.copy(localScripts, includedScripts.listFiles());
					} catch (IOException e) {
						logger.error(MessageFormat.format(
								"Failed to copy included Groovy scripts from {0} to {1}",
								includedScripts, localScripts));
					}
				}
			}
		}
		
		settingsModel = loadSettingModels(referencePropertiesInputStream);
		serverStatus.servletContainer = servletContext.getServerInfo();
	}

	/**
	 * Gitblit is being shutdown either because the servlet container is
	 * shutting down or because the servlet container is re-deploying Gitblit.
	 */
	@Override
	public void contextDestroyed(ServletContextEvent contextEvent) {
		logger.info("Gitblit context destroyed by servlet container.");
		scheduledExecutor.shutdownNow();
		luceneExecutor.close();
		gcExecutor.close();
	}
	
	/**
	 * 
	 * @return true if we are running the gc executor
	 */
	public boolean isCollectingGarbage() {
		return gcExecutor.isRunning();
	}
	
	/**
	 * Returns true if Gitblit is actively collecting garbage in this repository.
	 * 
	 * @param repositoryName
	 * @return true if actively collecting garbage
	 */
	public boolean isCollectingGarbage(String repositoryName) {
		return gcExecutor.isCollectingGarbage(repositoryName);
	}

	/**
	 * Creates a personal fork of the specified repository. The clone is view
	 * restricted by default and the owner of the source repository is given
	 * access to the clone. 
	 * 
	 * @param repository
	 * @param user
	 * @return the repository model of the fork, if successful
	 * @throws GitBlitException
	 */
	public RepositoryModel fork(RepositoryModel repository, UserModel user) throws GitBlitException {
		String cloneName = MessageFormat.format("~{0}/{1}.git", user.username, StringUtils.stripDotGit(StringUtils.getLastPathElement(repository.name)));
		String fromUrl = MessageFormat.format("file://{0}/{1}", repositoriesFolder.getAbsolutePath(), repository.name);

		// clone the repository
		try {
			JGitUtils.cloneRepository(repositoriesFolder, cloneName, fromUrl, true, null);
		} catch (Exception e) {
			throw new GitBlitException(e);
		}

		// create a Gitblit repository model for the clone
		RepositoryModel cloneModel = repository.cloneAs(cloneName);
		// owner has REWIND/RW+ permissions
		cloneModel.owner = user.username;
		updateRepositoryModel(cloneName, cloneModel, false);

		// add the owner of the source repository to the clone's access list
		if (!StringUtils.isEmpty(repository.owner)) {
			UserModel originOwner = getUserModel(repository.owner);
			if (originOwner != null) {
				originOwner.setRepositoryPermission(cloneName, AccessPermission.CLONE);
				updateUserModel(originOwner.username, originOwner, false);
			}
		}

		// grant origin's user list clone permission to fork
		List<String> users = getRepositoryUsers(repository);
		List<UserModel> cloneUsers = new ArrayList<UserModel>();
		for (String name : users) {
			if (!name.equalsIgnoreCase(user.username)) {
				UserModel cloneUser = getUserModel(name);
				if (cloneUser.canClone(repository)) {
					// origin user can clone origin, grant clone access to fork
					cloneUser.setRepositoryPermission(cloneName, AccessPermission.CLONE);
				}
				cloneUsers.add(cloneUser);
			}
		}
		userService.updateUserModels(cloneUsers);

		// grant origin's team list clone permission to fork
		List<String> teams = getRepositoryTeams(repository);
		List<TeamModel> cloneTeams = new ArrayList<TeamModel>();
		for (String name : teams) {
			TeamModel cloneTeam = getTeamModel(name);
			if (cloneTeam.canClone(repository)) {
				// origin team can clone origin, grant clone access to fork
				cloneTeam.setRepositoryPermission(cloneName, AccessPermission.CLONE);
			}
			cloneTeams.add(cloneTeam);
		}
		userService.updateTeamModels(cloneTeams);			

		// add this clone to the cached model
		addToCachedRepositoryList(cloneModel);
		return cloneModel;
	}

	/**
	 * Allow to understand if GitBlit supports and is configured to allow
	 * cookie-based authentication.
	 * 
	 * @return status of Cookie authentication enablement.
	 */
	public boolean allowCookieAuthentication() {
		return GitBlit.getBoolean(Keys.web.allowCookieAuthentication, true) && userService.supportsCookies();
	}
}
