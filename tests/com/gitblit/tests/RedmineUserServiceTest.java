package com.gitblit.tests;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

import java.util.HashMap;

import org.junit.Test;

import com.gitblit.RedmineUserService;
import com.gitblit.models.UserModel;
import com.gitblit.tests.mock.MemorySettings;

public class RedmineUserServiceTest {

    private static final String JSON = "{\"user\":{\"created_on\":\"2011-03-28T00:41:29Z\",\"lastname\":\"foo\","
        + "\"last_login_on\":\"2012-09-06T23:59:26Z\",\"firstname\":\"baz\","
        + "\"id\":4,\"login\":\"RedmineUserId\",\"mail\":\"baz@example.com\"}}";

    private static final String NOT_ADMIN_JSON = "{\"user\":{\"lastname\":\"foo\","
        + "\"last_login_on\":\"2012-09-08T13:59:01Z\",\"created_on\":\"2009-03-17T14:25:50Z\","
        + "\"mail\":\"baz@example.com\",\"id\":5,\"firstname\":\"baz\"}}";

    @Test
    public void testAuthenticate() throws Exception {
        RedmineUserService redmineUserService = new RedmineUserService();
        redmineUserService.setup(new MemorySettings(new HashMap<String, Object>()));
        redmineUserService.setTestingCurrentUserAsJson(JSON);
        UserModel userModel = redmineUserService.authenticate("RedmineUserId", "RedmineAPIKey".toCharArray());
        assertThat(userModel.getName(), is("RedmineUserId"));
        assertThat(userModel.getDisplayName(), is("baz foo"));
        assertThat(userModel.emailAddress, is("baz@example.com"));
        assertNotNull(userModel.cookie);
        assertThat(userModel.canAdmin, is(true));
    }

    @Test
    public void testAuthenticateNotAdminUser() throws Exception {
        RedmineUserService redmineUserService = new RedmineUserService();
        redmineUserService.setup(new MemorySettings(new HashMap<String, Object>()));
        redmineUserService.setTestingCurrentUserAsJson(NOT_ADMIN_JSON);
        UserModel userModel = redmineUserService.authenticate("RedmineUserId", "RedmineAPIKey".toCharArray());
        assertThat(userModel.getName(), is("baz@example.com"));
        assertThat(userModel.getDisplayName(), is("baz foo"));
        assertThat(userModel.emailAddress, is("baz@example.com"));
        assertNotNull(userModel.cookie);
        assertThat(userModel.canAdmin, is(false));
    }

}
