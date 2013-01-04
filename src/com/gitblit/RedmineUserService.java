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

        String file = settings.getString(Keys.realm.redmine.backingUserService, "users.conf");
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

        String urlText = this.settings.getString(Keys.realm.redmine.url, "");
        if (!urlText.endsWith("/")) {
            urlText.concat("/");
        }
        String apiKey = String.valueOf(password);

        try {
            String jsonString = getCurrentUserAsJson(urlText, apiKey);

            RedmineCurrent current = new Gson().fromJson(jsonString, RedmineCurrent.class);
            String login = current.user.login;

            boolean canAdmin = true;
            if (StringUtils.isEmpty(login)) {
                login = current.user.mail;
                
            	// non admin user can not get login name
            	// TODO review this assumption, if it is true, it is undocumented
                canAdmin = false;
            }
            
            UserModel user = getUserModel(login);
            if (user == null)	// create user object for new authenticated user
            	user = new UserModel(login);
            
            // create a user cookie
			if (StringUtils.isEmpty(user.cookie) && !ArrayUtils.isEmpty(password)) {
				user.cookie = StringUtils.getSHA1(user.username + new String(password));
			}
            
            // update user attributes from Redmine
			user.accountType = getAccountType();
			user.canAdmin = canAdmin;
        	user.displayName = current.user.firstname + " " + current.user.lastname;
        	user.emailAddress = current.user.mail;
        	user.password = ExternalAccount;
        	
        	// TODO Redmine group mapping for administration & teams
        	// http://www.redmine.org/projects/redmine/wiki/Rest_Users
        	
        	// push the changes to the backing user service
        	super.updateUserModel(user);
        	
            return user;
        } catch (IOException e) {
            logger.error("authenticate", e);
        }
        return null;
    }

    private String getCurrentUserAsJson(String url, String apiKey) throws IOException {
        if (testingJson != null) { // for testing
            return testingJson;
        }

        String apiUrl = url + "users/current.json?key=" + apiKey;
        HttpURLConnection http = (HttpURLConnection) ConnectionUtils.openConnection(apiUrl, null, null);
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
