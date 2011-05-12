package com.gitblit;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.security.Principal;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.security.auth.Subject;

import org.eclipse.jetty.http.security.Credential;
import org.eclipse.jetty.security.IdentityService;
import org.eclipse.jetty.security.MappedLoginService;
import org.eclipse.jetty.server.UserIdentity;
import org.eclipse.jetty.util.log.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.models.UserModel;

public class JettyLoginService extends MappedLoginService implements ILoginService {

	private final Logger logger = LoggerFactory.getLogger(JettyLoginService.class);

	private final File realmFile;

	public JettyLoginService(File realmFile) {
		super();
		setName(Constants.NAME);
		this.realmFile = realmFile;
	}

	@Override
	public UserModel authenticate(String username, char[] password) {
		UserIdentity identity = login(username, new String(password));
		if (identity == null || identity.equals(UserIdentity.UNAUTHENTICATED_IDENTITY)) {
			return null;
		}
		UserModel user = new UserModel(username);
		user.setCookie(StringUtils.getSHA1((Constants.NAME + username + new String(password))));
		user.canAdmin(identity.isUserInRole(Constants.ADMIN_ROLE, null));

		// Add repositories
		for (Principal principal : identity.getSubject().getPrincipals()) {
			if (principal instanceof RolePrincipal) {
				RolePrincipal role = (RolePrincipal) principal;
				String roleName = role.getName();
				if (roleName.charAt(0) != '#') {
					user.addRepository(roleName);
				}
			}
		}
		return user;
	}

	@Override
	public UserModel authenticate(char[] cookie) {
		// TODO cookie login
		return null;
	}

	@Override
	public UserModel getUserModel(String username) {
		UserModel model = new UserModel(username);
		UserIdentity identity = _users.get(username);
		Subject subject = identity.getSubject();
		for (Principal principal : subject.getPrincipals()) {
			if (principal instanceof RolePrincipal) {
				RolePrincipal role = (RolePrincipal) principal;
				String name = role.getName();
				switch (name.charAt(0)) {
				case '#':
					// Permissions
					if (name.equalsIgnoreCase(Constants.ADMIN_ROLE)) {
						model.canAdmin(true);
					}
					break;
				default:
					model.addRepository(name);
				}
			}
		}
		// Retrieve the password from the realm file.
		// Stupid, I know, but the password is buried within protected inner
		// classes in private variables. Too much work to reflectively retrieve.
		try {
			Properties allUsers = readRealmFile();
			String value = allUsers.getProperty(username);
			String password = value.split(",")[0];
			model.setPassword(password);
		} catch (Throwable t) {
			logger.error(MessageFormat.format("Failed to read password for user {0}!", username), t);
		}
		return model;
	}

	@Override
	public boolean updateUserModel(UserModel model) {
		try {
			Properties allUsers = readRealmFile();
			ArrayList<String> roles = new ArrayList<String>(model.getRepositories());

			// Permissions
			if (model.canAdmin()) {
				roles.add(Constants.ADMIN_ROLE);
			}

			StringBuilder sb = new StringBuilder();
			sb.append(model.getPassword());
			sb.append(',');
			for (String role : roles) {
				sb.append(role);
				sb.append(',');
			}
			// trim trailing comma
			sb.setLength(sb.length() - 1);
			allUsers.put(model.getUsername(), sb.toString());

			writeRealmFile(allUsers);

			// Update login service
			putUser(model.getUsername(), Credential.getCredential(model.getPassword()), roles.toArray(new String[0]));
			return true;
		} catch (Throwable t) {
			logger.error(MessageFormat.format("Failed to update user model {0}!", model.getUsername()), t);
		}
		return false;
	}

	@Override
	public boolean deleteUserModel(UserModel model) {
		try {
			// Read realm file
			Properties allUsers = readRealmFile();
			allUsers.remove(model.getUsername());
			writeRealmFile(allUsers);

			// Drop user from map
			_users.remove(model.getUsername());
			return true;
		} catch (Throwable t) {
			logger.error(MessageFormat.format("Failed to delete user model {0}!", model.getUsername()), t);
		}
		return false;
	}

	@Override
	public List<String> getAllUsernames() {
		List<String> list = new ArrayList<String>();
		list.addAll(_users.keySet());
		return list;
	}

	@Override
	public List<String> getUsernamesForRole(String role) {
		List<String> list = new ArrayList<String>();
		try {
			Properties allUsers = readRealmFile();
			for (String username : allUsers.stringPropertyNames()) {
				String value = allUsers.getProperty(username);
				String[] values = value.split(",");
				// skip first value (password)
				for (int i = 1; i < values.length; i++) {
					String r = values[i];
					if (r.equalsIgnoreCase(role)) {
						list.add(username);
						break;
					}
				}
			}
		} catch (Throwable t) {
			logger.error(MessageFormat.format("Failed to get usernames for role {0}!", role), t);
		}
		return list;
	}

	@Override
	public boolean setUsernamesForRole(String role, List<String> usernames) {
		try {
			Set<String> specifiedUsers = new HashSet<String>(usernames);
			Set<String> needsAddRole = new HashSet<String>(specifiedUsers);
			Set<String> needsRemoveRole = new HashSet<String>();

			// identify users which require add and remove role
			Properties allUsers = readRealmFile();
			for (String username : allUsers.stringPropertyNames()) {
				String value = allUsers.getProperty(username);
				String[] values = value.split(",");
				// skip first value (password)
				for (int i = 1; i < values.length; i++) {
					String r = values[i];
					if (r.equalsIgnoreCase(role)) {
						// user has role, check against revised user list
						if (specifiedUsers.contains(username)) {
							needsAddRole.remove(username);
						} else {
							// remove role from user
							needsRemoveRole.add(username);
						}
						break;
					}
				}
			}

			// add roles to users
			for (String user : needsAddRole) {
				String userValues = allUsers.getProperty(user);
				userValues += ("," + role);
				allUsers.put(user, userValues);
				String[] values = userValues.split(",");
				String password = values[0];
				String[] roles = new String[values.length - 1];
				System.arraycopy(values, 1, roles, 0, values.length - 1);
				putUser(user, Credential.getCredential(password), roles);
			}

			// remove role from user
			for (String user : needsRemoveRole) {
				String[] values = allUsers.getProperty(user).split(",");
				String password = values[0];
				StringBuilder sb = new StringBuilder();
				sb.append(password);
				sb.append(',');
				List<String> revisedRoles = new ArrayList<String>();
				// skip first value (password)
				for (int i = 1; i < values.length; i++) {
					String value = values[i];
					if (!value.equalsIgnoreCase(role)) {
						revisedRoles.add(value);
						sb.append(value);
						sb.append(',');
					}
				}
				sb.setLength(sb.length() - 1);

				// update properties
				allUsers.put(user, sb.toString());

				// update memory
				putUser(user, Credential.getCredential(password), revisedRoles.toArray(new String[0]));
			}

			// persist changes
			writeRealmFile(allUsers);
			return true;
		} catch (Throwable t) {
			logger.error(MessageFormat.format("Failed to set usernames for role {0}!", role), t);
		}
		return false;
	}

	@Override
	public boolean renameRole(String oldRole, String newRole) {
		try {
			Properties allUsers = readRealmFile();
			Set<String> needsRenameRole = new HashSet<String>();

			// identify users which require role rename
			for (String username : allUsers.stringPropertyNames()) {
				String value = allUsers.getProperty(username);
				String[] roles = value.split(",");
				// skip first value (password)
				for (int i = 1; i < roles.length; i++) {
					String r = roles[i];
					if (r.equalsIgnoreCase(oldRole)) {
						needsRenameRole.remove(username);
						break;
					}
				}
			}

			// rename role for identified users
			for (String user : needsRenameRole) {
				String userValues = allUsers.getProperty(user);
				String[] values = userValues.split(",");
				String password = values[0];
				StringBuilder sb = new StringBuilder();
				sb.append(password);
				sb.append(',');
				List<String> revisedRoles = new ArrayList<String>();
				revisedRoles.add(newRole);
				// skip first value (password)
				for (int i = 1; i < values.length; i++) {
					String value = values[i];
					if (!value.equalsIgnoreCase(oldRole)) {
						revisedRoles.add(value);
						sb.append(value);
						sb.append(',');
					}
				}
				sb.setLength(sb.length() - 1);

				// update properties
				allUsers.put(user, sb.toString());

				// update memory
				putUser(user, Credential.getCredential(password), revisedRoles.toArray(new String[0]));
			}

			// persist changes
			writeRealmFile(allUsers);
			return true;
		} catch (Throwable t) {
			logger.error(MessageFormat.format("Failed to rename role {0} to {1}!", oldRole, newRole), t);
		}
		return false;
	}

	@Override
	public boolean deleteRole(String role) {
		try {
			Properties allUsers = readRealmFile();
			Set<String> needsDeleteRole = new HashSet<String>();

			// identify users which require role rename
			for (String username : allUsers.stringPropertyNames()) {
				String value = allUsers.getProperty(username);
				String[] roles = value.split(",");
				// skip first value (password)
				for (int i = 1; i < roles.length; i++) {
					String r = roles[i];
					if (r.equalsIgnoreCase(role)) {
						needsDeleteRole.remove(username);
						break;
					}
				}
			}

			// delete role for identified users
			for (String user : needsDeleteRole) {
				String userValues = allUsers.getProperty(user);
				String[] values = userValues.split(",");
				String password = values[0];
				StringBuilder sb = new StringBuilder();
				sb.append(password);
				sb.append(',');
				List<String> revisedRoles = new ArrayList<String>();
				// skip first value (password)
				for (int i = 1; i < values.length; i++) {
					String value = values[i];
					if (!value.equalsIgnoreCase(role)) {
						revisedRoles.add(value);
						sb.append(value);
						sb.append(',');
					}
				}
				sb.setLength(sb.length() - 1);

				// update properties
				allUsers.put(user, sb.toString());

				// update memory
				putUser(user, Credential.getCredential(password), revisedRoles.toArray(new String[0]));
			}

			// persist changes
			writeRealmFile(allUsers);
		} catch (Throwable t) {
			logger.error(MessageFormat.format("Failed to delete role {0}!", role), t);
		}
		return false;
	}

	private Properties readRealmFile() throws IOException {
		Properties allUsers = new Properties();
		FileReader reader = new FileReader(realmFile);
		allUsers.load(reader);
		reader.close();
		return allUsers;
	}

	private void writeRealmFile(Properties properties) throws IOException {
		// Update realm file
		File realmFileCopy = new File(realmFile.getAbsolutePath() + ".tmp");
		FileWriter writer = new FileWriter(realmFileCopy);
		properties.store(writer, "# Git:Blit realm file format: username=password,\\#permission,repository1,repository2...");
		writer.close();
		if (realmFileCopy.exists() && realmFileCopy.length() > 0) {
			realmFile.delete();
			realmFileCopy.renameTo(realmFile);
		} else {
			throw new IOException("Failed to save realmfile!");
		}
	}

	/* ------------------------------------------------------------ */
	@Override
	public void loadUsers() throws IOException {
		if (realmFile == null)
			return;

		if (Log.isDebugEnabled())
			Log.debug("Load " + this + " from " + realmFile);
		Properties allUsers = readRealmFile();

		// Map Users
		for (Map.Entry<Object, Object> entry : allUsers.entrySet()) {
			String username = ((String) entry.getKey()).trim();
			String credentials = ((String) entry.getValue()).trim();
			String roles = null;
			int c = credentials.indexOf(',');
			if (c > 0) {
				roles = credentials.substring(c + 1).trim();
				credentials = credentials.substring(0, c).trim();
			}

			if (username != null && username.length() > 0 && credentials != null && credentials.length() > 0) {
				String[] roleArray = IdentityService.NO_ROLES;
				if (roles != null && roles.length() > 0) {
					roleArray = roles.split(",");
				}
				putUser(username, Credential.getCredential(credentials), roleArray);
			}
		}
	}

	@Override
	protected UserIdentity loadUser(String username) {
		return null;
	}
}
