package com.gitblit;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Map;
import java.util.Properties;

import javax.security.auth.Subject;

import org.eclipse.jetty.http.security.Credential;
import org.eclipse.jetty.security.IdentityService;
import org.eclipse.jetty.security.MappedLoginService;
import org.eclipse.jetty.server.UserIdentity;
import org.eclipse.jetty.util.log.Log;

import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.models.User;

public class JettyLoginService extends MappedLoginService implements ILoginService {

	private final File realmFile;

	public JettyLoginService(File realmFile) {
		super();
		setName(Constants.NAME);
		this.realmFile = realmFile;
	}

	@Override
	public User authenticate(String username, char[] password) {
		UserIdentity identity = login(username, new String(password));
		if (identity == null || identity.equals(UserIdentity.UNAUTHENTICATED_IDENTITY)) {
			return null;
		}
		User user = new User(username);
		user.setCookie(StringUtils.getSHA1((Constants.NAME + username + new String(password))));
		user.canAdmin(identity.isUserInRole(Constants.ADMIN_ROLE, null));

		// Add repositories
		for (Principal principal : identity.getSubject().getPrincipals()) {
			if (principal instanceof RolePrincipal) {
				RolePrincipal role = (RolePrincipal) principal;
				if (role.getName().charAt(0) != '#') {
					user.addRepository(role.getName().substring(1));
				}
			}
		}
		return user;
	}

	@Override
	public User authenticate(char[] cookie) {
		// TODO cookie login
		return null;
	}

	@Override
	public User getUserModel(String username) {
		User model = new User(username);
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
					model.addRepository(name.substring(1));
				}
			}
		}
		return model;
	}

	@Override
	public boolean updateUserModel(User model) {
		try {
			Properties properties = new Properties();
			FileReader reader = new FileReader(realmFile);
			properties.load(reader);
			reader.close();

			ArrayList<String> roles = new ArrayList<String>();

			// Repositories
			roles.addAll(model.getRepositories());

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

			// Update realm file
			File realmFileCopy = new File(realmFile.getAbsolutePath() + ".tmp");
			FileWriter writer = new FileWriter(realmFileCopy);
			properties.put(model.getUsername(), sb.toString());
			properties.store(writer, null);
			writer.close();
			realmFile.delete();
			realmFileCopy.renameTo(realmFile);

			// Update login service
			putUser(model.getUsername(), Credential.getCredential(model.getPassword()), roles.toArray(new String[0]));
			return true;
		} catch (Throwable t) {
			t.printStackTrace();
		}
		return false;
	}

	@Override
	public boolean deleteUserModel(User model) {
		try {
			// Read realm file
			Properties properties = new Properties();
			FileReader reader = new FileReader(realmFile);
			properties.load(reader);
			reader.close();
			properties.remove(model.getUsername());

			// Update realm file
			File realmFileCopy = new File(realmFile.getAbsolutePath() + ".tmp");
			FileWriter writer = new FileWriter(realmFileCopy);
			properties.store(writer, null);
			writer.close();
			realmFile.delete();
			realmFileCopy.renameTo(realmFile);

			// Drop user from map
			_users.remove(model.getUsername());
			return true;
		} catch (Throwable t) {
			t.printStackTrace();
		}
		return false;
	}

	/* ------------------------------------------------------------ */
	@Override
	public void loadUsers() throws IOException {
		if (realmFile == null)
			return;

		if (Log.isDebugEnabled())
			Log.debug("Load " + this + " from " + realmFile);
		Properties properties = new Properties();
		FileReader reader = new FileReader(realmFile);
		properties.load(reader);
		reader.close();

		// Map Users
		for (Map.Entry<Object, Object> entry : properties.entrySet()) {
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
