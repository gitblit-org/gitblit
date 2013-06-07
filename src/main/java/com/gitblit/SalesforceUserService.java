package com.gitblit;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.models.UserModel;
import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.StringUtils;
import com.sforce.soap.partner.Connector;
import com.sforce.soap.partner.GetUserInfoResult;
import com.sforce.soap.partner.PartnerConnection;
import com.sforce.ws.ConnectionException;
import com.sforce.ws.ConnectorConfig;

public class SalesforceUserService extends GitblitUserService {
	public static final Logger logger = LoggerFactory
			.getLogger(SalesforceUserService.class);
	private IStoredSettings settings;

	@Override
	public void setup(IStoredSettings settings) {
		this.settings = settings;
		String file = settings.getString(
				Keys.realm.salesforce.backingUserService,
				"${baseFolder}/users.conf");
		File realmFile = GitBlit.getFileOrFolder(file);

		serviceImpl = createUserService(realmFile);

		logger.info("Salesforce User Service backed by "
				+ serviceImpl.toString());
	}

	@Override
	public UserModel authenticate(String username, char[] password) {
		if (isLocalAccount(username)) {
			// local account, bypass Salesforce authentication
			return super.authenticate(username, password);
		}

		ConnectorConfig config = new ConnectorConfig();
		config.setUsername(username);
		config.setPassword(new String(password));

		try {
			PartnerConnection connection = Connector.newConnection(config);

			GetUserInfoResult info = connection.getUserInfo();

			String org = settings.getString(Keys.realm.salesforce.orgId, "0")
					.trim();

			if (!org.equals("0")) {
				if (!org.equals(info.getOrganizationId())) {
					logger.warn("Access attempted by user of an invalid org: "
							+ info.getUserName() + ", org: "
							+ info.getOrganizationName() + "("
							+ info.getOrganizationId() + ")");

					return null;
				}
			}

			logger.info("Authenticated user " + info.getUserName()
					+ " using org " + info.getOrganizationName() + "("
					+ info.getOrganizationId() + ")");

			String simpleUsername = getSimpleUsername(info);

			UserModel user = null;
			synchronized (this) {
				user = getUserModel(simpleUsername);
				if (user == null)
					user = new UserModel(simpleUsername);

				if (StringUtils.isEmpty(user.cookie)
						&& !ArrayUtils.isEmpty(password)) {
					user.cookie = StringUtils.getSHA1(user.username
							+ new String(password));
				}

				setUserAttributes(user, info);

				super.updateUserModel(user);
			}

			return user;
		} catch (ConnectionException e) {
			logger.error("Failed to authenticate", e);
		}

		return null;
	}

	private void setUserAttributes(UserModel user, GetUserInfoResult info) {
		// Don't want visibility into the real password, make up a dummy
		user.password = ExternalAccount;
		user.accountType = getAccountType();

		// Get full name Attribute
		user.displayName = info.getUserFullName();

		// Get email address Attribute
		user.emailAddress = info.getUserEmail();
	}

	/**
	 * Simple user name is the first part of the email address.
	 */
	private String getSimpleUsername(GetUserInfoResult info) {
		String email = info.getUserEmail();

		return email.split("@")[0];
	}

	@Override
	public boolean supportsCredentialChanges() {
		return false;
	}

	@Override
	public boolean supportsDisplayNameChanges() {
		return false;
	}

	@Override
	public boolean supportsEmailAddressChanges() {
		return false;
	}
}
