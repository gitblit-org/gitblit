package com.gitblit.tests;

import static org.hamcrest.CoreMatchers.is;

import java.util.HashMap;

import org.junit.Test;

import com.gitblit.IStoredSettings;
import com.gitblit.auth.RedmineAuthProvider;
import com.gitblit.manager.RuntimeManager;
import com.gitblit.manager.UserManager;
import com.gitblit.models.UserModel;
import com.gitblit.tests.mock.MemorySettings;

public class RedmineAuthenticationTest extends GitblitUnitTest {

    private static final String JSON = "{\"user\":{\"created_on\":\"2011-03-28T00:41:29Z\",\"lastname\":\"foo\","
        + "\"last_login_on\":\"2012-09-06T23:59:26Z\",\"firstname\":\"baz\","
        + "\"id\":4,\"login\":\"RedmineUserId\",\"mail\":\"baz@example.com\"}}";

    private static final String NOT_ADMIN_JSON = "{\"user\":{\"lastname\":\"foo\","
        + "\"last_login_on\":\"2012-09-08T13:59:01Z\",\"created_on\":\"2009-03-17T14:25:50Z\","
        + "\"mail\":\"baz@example.com\",\"id\":5,\"firstname\":\"baz\"}}";

    MemorySettings getSettings() {
    	return new MemorySettings(new HashMap<String, Object>());
    }

    RedmineAuthProvider newRedmineAuthentication(IStoredSettings settings) {
    	RuntimeManager runtime = new RuntimeManager(settings, GitBlitSuite.BASEFOLDER).start();
    	UserManager users = new UserManager(runtime).start();
    	RedmineAuthProvider redmine = new RedmineAuthProvider();
    	redmine.setup(runtime, users);
    	return redmine;
    }

    RedmineAuthProvider newRedmineAuthentication() {
    	return newRedmineAuthentication(getSettings());
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
        assertThat(userModel.canAdmin, is(true));
    }

    @Test
    public void testAuthenticateNotAdminUser() throws Exception {
    	RedmineAuthProvider redmine = newRedmineAuthentication();
        redmine.setTestingCurrentUserAsJson(NOT_ADMIN_JSON);
        UserModel userModel = redmine.authenticate("RedmineUserId", "RedmineAPIKey".toCharArray());
        assertThat(userModel.getName(), is("redmineuserid"));
        assertThat(userModel.getDisplayName(), is("baz foo"));
        assertThat(userModel.emailAddress, is("baz@example.com"));
        assertNotNull(userModel.cookie);
        assertThat(userModel.canAdmin, is(false));
    }
}
