package com.gitblit.tests;

import static org.hamcrest.CoreMatchers.is;

import java.util.HashMap;

import org.junit.Test;

import com.gitblit.IStoredSettings;
import com.gitblit.auth.RedmineAuthProvider;
import com.gitblit.manager.AuthenticationManager;
import com.gitblit.manager.RuntimeManager;
import com.gitblit.manager.UserManager;
import com.gitblit.models.UserModel;
import com.gitblit.tests.mock.MemorySettings;
import com.gitblit.utils.XssFilter;
import com.gitblit.utils.XssFilter.AllowXssFilter;

public class RedmineAuthenticationTest extends GitblitUnitTest {

    private static final String JSON = "{\"user\":{\"created_on\":\"2011-03-28T00:41:29Z\",\"lastname\":\"foo\","
        + "\"last_login_on\":\"2012-09-06T23:59:26Z\",\"firstname\":\"baz\","
        + "\"id\":4,\"login\":\"RedmineUserId\",\"mail\":\"baz@example.com\"}}";

    MemorySettings getSettings() {
    	return new MemorySettings(new HashMap<String, Object>());
    }

    RedmineAuthProvider newRedmineAuthentication(IStoredSettings settings) {
    	XssFilter xssFilter = new AllowXssFilter();
    	RuntimeManager runtime = new RuntimeManager(settings, xssFilter, GitBlitSuite.BASEFOLDER).start();
    	UserManager users = new UserManager(runtime, null).start();
    	RedmineAuthProvider redmine = new RedmineAuthProvider();
    	redmine.setup(runtime, users);
    	return redmine;
    }

    RedmineAuthProvider newRedmineAuthentication() {
    	return newRedmineAuthentication(getSettings());
    }

    AuthenticationManager newAuthenticationManager() {
    	XssFilter xssFilter = new AllowXssFilter();
    	RuntimeManager runtime = new RuntimeManager(getSettings(), xssFilter, GitBlitSuite.BASEFOLDER).start();
    	UserManager users = new UserManager(runtime, null).start();
    	RedmineAuthProvider redmine = new RedmineAuthProvider();
    	redmine.setup(runtime, users);
        redmine.setTestingCurrentUserAsJson(JSON);
    	AuthenticationManager auth = new AuthenticationManager(runtime, users);
    	auth.addAuthenticationProvider(redmine);
    	return auth;
    }

    @Test
    public void testAuthenticate() throws Exception {
    	RedmineAuthProvider redmine = newRedmineAuthentication();
        redmine.setTestingCurrentUserAsJson(JSON);
        UserModel userModel = redmine.authenticate("RedmineAdminId", "RedmineAPIKey".toCharArray());
        assertThat(userModel.getName(), is("redmineadminid"));
        assertThat(userModel.getDisplayName(), is("baz foo"));
        assertThat(userModel.emailAddress, is("baz@example.com"));
        assertNotNull(userModel.cookie);
    }

    @Test
    public void testAuthenticationManager() throws Exception {
    	AuthenticationManager auth = newAuthenticationManager();
        UserModel userModel = auth.authenticate("RedmineAdminId", "RedmineAPIKey".toCharArray());
        assertThat(userModel.getName(), is("redmineadminid"));
        assertThat(userModel.getDisplayName(), is("baz foo"));
        assertThat(userModel.emailAddress, is("baz@example.com"));
        assertNotNull(userModel.cookie);
    }
}
