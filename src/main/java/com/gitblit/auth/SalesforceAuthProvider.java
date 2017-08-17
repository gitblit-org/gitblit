package com.gitblit.auth;

import com.gitblit.Constants;
import com.gitblit.Constants.AccountType;
import com.gitblit.Constants.Role;
import com.gitblit.Keys;
import com.gitblit.auth.AuthenticationProvider.UsernamePasswordAuthenticationProvider;
import com.gitblit.models.TeamModel;
import com.gitblit.models.UserModel;
import com.sforce.soap.partner.Connector;
import com.sforce.soap.partner.GetUserInfoResult;
import com.sforce.soap.partner.PartnerConnection;
import com.sforce.ws.ConnectionException;
import com.sforce.ws.ConnectorConfig;

public class SalesforceAuthProvider extends UsernamePasswordAuthenticationProvider {

	public SalesforceAuthProvider() {
		super("salesforce");
	}

	@Override
	public AccountType getAccountType() {
		return AccountType.SALESFORCE;
	}

	@Override
	public void setup() {
	}

	@Override
	public UserModel authenticate(String username, char[] password) {
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
				user = userManager.getUserModel(simpleUsername);
				if (user == null) {
					user = new UserModel(simpleUsername);
				}

				setCookie(user);
				setUserAttributes(user, info);

				updateUser(user);
			}

			return user;
		} catch (ConnectionException e) {
			logger.error("Failed to authenticate", e);
		}

		return null;
	}

	private void setUserAttributes(UserModel user, GetUserInfoResult info) {
		// Don't want visibility into the real password, make up a dummy
		user.password = Constants.EXTERNAL_ACCOUNT;
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

	@Override
	public boolean supportsTeamMembershipChanges() {
		return true;
	}

    @Override
    public boolean supportsRoleChanges(UserModel user, Role role) {
        return true;
    }

	@Override
	public boolean supportsRoleChanges(TeamModel team, Role role) {
		return true;
	}

}
