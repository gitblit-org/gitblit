package com.gitblit;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;

import org.apache.wicket.util.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.Constants.AccountType;
import com.gitblit.models.UserModel;
import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.ConnectionUtils;
import com.gitblit.utils.StringUtils;
import com.google.gson.Gson;

/**
 * Implementation of an Redmine user service.<br>
 * you can login to gitblit with Redmine user id and api key.
 */
public class RedmineUserService extends GitblitUserService {

    private final Logger logger = LoggerFactory.getLogger(RedmineUserService.class);

    private IStoredSettings settings;

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

    public RedmineUserService() {
        super();
    }

    @Override
    public void setup(IStoredSettings settings) {
        this.settings = settings;

        String file = settings.getString(Keys.realm.redmine.backingUserService, "${baseFolder}/users.conf");
        File realmFile = GitBlit.getFileOrFolder(file);

        serviceImpl = createUserService(realmFile);
        logger.info("Redmine User Service backed by " + serviceImpl.toString());
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
	protected AccountType getAccountType() {
		return AccountType.REDMINE;
	}

    @Override
    public UserModel authenticate(String username, char[] password) {
		if (isLocalAccount(username)) {
			// local account, bypass Redmine authentication
			return super.authenticate(username, password);
		}

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

        UserModel user = getUserModel(username);
        if (user == null)	// create user object for new authenticated user
        	user = new UserModel(username.toLowerCase());

        // create a user cookie
        if (StringUtils.isEmpty(user.cookie) && !ArrayUtils.isEmpty(password)) {
        	user.cookie = StringUtils.getSHA1(user.username + new String(password));
        }

        // update user attributes from Redmine
        user.accountType = getAccountType();
        user.displayName = current.user.firstname + " " + current.user.lastname;
        user.emailAddress = current.user.mail;
        user.password = Constants.EXTERNAL_ACCOUNT;
        if (!StringUtils.isEmpty(current.user.login)) {
        	// only admin users can get login name
        	// evidently this is an undocumented behavior of Redmine
        	user.canAdmin = true;
        }

        // TODO consider Redmine group mapping for team membership
        // http://www.redmine.org/projects/redmine/wiki/Rest_Users

        // push the changes to the backing user service
        super.updateUserModel(user);

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
        HttpURLConnection http;
        if (username == null) {
        	// apikey authentication
        	String apiKey = String.valueOf(password);
        	String apiUrl = url + "users/current.json?key=" + apiKey;
        	http = (HttpURLConnection) ConnectionUtils.openConnection(apiUrl, null, null);
        } else {
        	// username/password BASIC authentication
        	String apiUrl = url + "users/current.json";
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
