/*
 * Copyright 2012 gitblit.com.
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
package com.gitblit.auth;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;

import org.apache.commons.io.IOUtils;

import com.gitblit.Constants;
import com.gitblit.Constants.AccountType;
import com.gitblit.Constants.Role;
import com.gitblit.Keys;
import com.gitblit.auth.AuthenticationProvider.UsernamePasswordAuthenticationProvider;
import com.gitblit.models.TeamModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.ConnectionUtils;
import com.gitblit.utils.StringUtils;
import com.google.gson.Gson;

/**
 * Implementation of Redmine authentication.<br>
 * you can login to gitblit with Redmine user id and api key.
 */
public class RedmineAuthProvider extends UsernamePasswordAuthenticationProvider {

    private String testingJson;

    private class RedmineCurrent {
        private class RedmineUser {
            public String login;
            public String firstname;
            public String lastname;
            public String mail;
        }

        public RedmineUser user;
    }

    public RedmineAuthProvider() {
        super("redmine");
    }

    @Override
    public void setup() {
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
        return false;
    }

    @Override
    public boolean supportsRoleChanges(UserModel user, Role role) {
        return true;
    }

	@Override
	public boolean supportsRoleChanges(TeamModel team, Role role) {
		return true;
	}

	 @Override
	public AccountType getAccountType() {
		return AccountType.REDMINE;
	}

    @Override
    public UserModel authenticate(String username, char[] password) {
        String jsonString = null;
        try {
        	// first attempt by username/password
        	jsonString = getCurrentUserAsJson(username, password);
        } catch (Exception e1) {
        	logger.warn("Failed to authenticate via username/password against Redmine");
        	try {
        		// second attempt is by apikey
        		jsonString = getCurrentUserAsJson(null, password);
        		username = null;
        	} catch (Exception e2) {
        		logger.error("Failed to authenticate via apikey against Redmine", e2);
        		return null;
        	}
        }

        if (StringUtils.isEmpty(jsonString)) {
        	logger.error("Received empty authentication response from Redmine");
        	return null;
        }

        RedmineCurrent current = null;
        try {
        	current = new Gson().fromJson(jsonString, RedmineCurrent.class);
        } catch (Exception e) {
        	logger.error("Failed to deserialize Redmine json response: " + jsonString, e);
        	return null;
        }

        if (StringUtils.isEmpty(username)) {
        	// if the username has been reset because of apikey authentication
        	// then use the email address of the user. this is the original
        	// behavior as contributed by github/mallowlabs
        	username = current.user.mail;
        }

        UserModel user = userManager.getUserModel(username);
        if (user == null) {
        	// create user object for new authenticated user
        	user = new UserModel(username.toLowerCase());
        }

        // create a user cookie
        setCookie(user);

        // update user attributes from Redmine
        user.accountType = getAccountType();
        user.displayName = current.user.firstname + " " + current.user.lastname;
        user.emailAddress = current.user.mail;
        user.password = Constants.EXTERNAL_ACCOUNT;

        // TODO consider Redmine group mapping for team membership
        // http://www.redmine.org/projects/redmine/wiki/Rest_Users

        // push the changes to the backing user service
        updateUser(user);

        return user;
    }

    private String getCurrentUserAsJson(String username, char [] password) throws IOException {
        if (testingJson != null) { // for testing
            return testingJson;
        }

        String url = this.settings.getString(Keys.realm.redmine.url, "");
        if (!url.endsWith("/")) {
        	url = url.concat("/");
        }
        String apiUrl = url + "users/current.json";

        HttpURLConnection http;
        if (username == null) {
        	// apikey authentication
        	String apiKey = String.valueOf(password);
        	http = (HttpURLConnection) ConnectionUtils.openConnection(apiUrl, null, null);
            http.addRequestProperty("X-Redmine-API-Key", apiKey);
        } else {
        	// username/password BASIC authentication
        	http = (HttpURLConnection) ConnectionUtils.openConnection(apiUrl, username, password);
        }
        http.setRequestMethod("GET");
        http.connect();
        InputStreamReader reader = new InputStreamReader(http.getInputStream());
        return IOUtils.toString(reader);
    }

    /**
     * set json response. do NOT invoke from production code.
     * @param json json
     */
    public void setTestingCurrentUserAsJson(String json) {
        this.testingJson = json;
    }
}
