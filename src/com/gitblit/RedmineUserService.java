package com.gitblit;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;

import org.apache.wicket.util.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.models.UserModel;
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
    public UserModel authenticate(String username, char[] password) {
        String urlText = this.settings.getString(Keys.realm.redmine.url, "");
        if (!urlText.endsWith("/")) {
            urlText.concat("/");
        }
        String apiKey = String.valueOf(password);

        try {
            String jsonString = getCurrentUserAsJson(urlText, apiKey);

            RedmineCurrent current = new Gson().fromJson(jsonString, RedmineCurrent.class);
            String login = current.user.login;

            if (username.equalsIgnoreCase(login)) {
                UserModel userModel = new UserModel(login);
                userModel.displayName = current.user.firstname + " " + current.user.lastname;
                userModel.emailAddress = current.user.mail;
                userModel.canAdmin = true;
                userModel.cookie = StringUtils.getSHA1(userModel.username + new String(password));
                return userModel;
            }

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
