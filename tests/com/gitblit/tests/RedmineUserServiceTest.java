package com.gitblit.tests;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;

import org.junit.Test;

import com.gitblit.RedmineUserService;
import com.gitblit.models.UserModel;
import com.gitblit.tests.mock.MemorySettings;
import com.gitblit.utils.StringUtils;

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
        UserModel userModel = redmineUserService.authenticate("RedmineAdminId", "RedmineAPIKey".toCharArray());
        assertThat(userModel.getName(), is("redmineadminid"));
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
        assertThat(userModel.getName(), is("redmineuserid"));
        assertThat(userModel.getDisplayName(), is("baz foo"));
        assertThat(userModel.emailAddress, is("baz@example.com"));
        assertNotNull(userModel.cookie);
        assertThat(userModel.canAdmin, is(false));
    }
    
    @Test
	public void testLocalAccount() {
        RedmineUserService redmineUserService = new RedmineUserService();
        redmineUserService.setup(new MemorySettings(new HashMap<String, Object>()));

		UserModel localAccount = new UserModel("bruce");
		localAccount.displayName = "Bruce Campbell";
		localAccount.password = StringUtils.MD5_TYPE + StringUtils.getMD5("gimmesomesugar");
		redmineUserService.deleteUser(localAccount.username);
		assertTrue("Failed to add local account",
				redmineUserService.updateUserModel(localAccount));
		assertEquals("Accounts are not equal!", 
				localAccount, 
				redmineUserService.authenticate(localAccount.username, "gimmesomesugar".toCharArray()));
		assertTrue("Failed to delete local account!",
				redmineUserService.deleteUser(localAccount.username));
	}

}
