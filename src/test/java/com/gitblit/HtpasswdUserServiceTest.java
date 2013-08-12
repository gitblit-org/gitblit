package com.gitblit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.HashMap;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.gitblit.HtpasswdUserService;
import com.gitblit.models.UserModel;
import com.gitblit.tests.mock.MemorySettings;
import com.gitblit.utils.StringUtils;

/**
 * Test the Htpasswd user service.
 *
 */
public class HtpasswdUserServiceTest {

    private static final String RESOURCE_DIR = "src/test/resources/htpasswdUSTest/";
    private static final String KEY_SUPPORT_PLAINTEXT_PWD = "realm.htpasswd.supportPlaintextPasswords";

    private static final int NUM_USERS_HTPASSWD = 10;

    private static final MemorySettings MS = new MemorySettings(new HashMap<String, Object>());

    private HtpasswdUserService htpwdUserService;


    private MemorySettings getSettings( String userfile, String groupfile, Boolean overrideLA)
    {
        MS.put("realm.htpasswd.backingUserService", RESOURCE_DIR + "users.conf");
        MS.put("realm.htpasswd.userfile", (userfile == null) ? (RESOURCE_DIR+"htpasswd") : userfile);
        MS.put("realm.htpasswd.groupfile", (groupfile == null) ? (RESOURCE_DIR+"htgroup") : groupfile);
        MS.put("realm.htpasswd.overrideLocalAuthentication", (overrideLA == null) ? "false" : overrideLA.toString());
        // Default to keep test the same on all platforms.
        MS.put(KEY_SUPPORT_PLAINTEXT_PWD, "false");

        return MS;
    }

    private MemorySettings getSettings()
    {
        return getSettings(null, null, null);
    }

    private MemorySettings getSettings(boolean overrideLA)
    {
        return getSettings(null, null, new Boolean(overrideLA));
    }


    private void setupUS()
    {
        htpwdUserService = new HtpasswdUserService();
        htpwdUserService.setup(getSettings());
    }

    private void setupUS(boolean overrideLA)
    {
        htpwdUserService = new HtpasswdUserService();
        htpwdUserService.setup(getSettings(overrideLA));
    }


    private void copyInFiles() throws IOException
    {
        File dir = new File(RESOURCE_DIR);
        FilenameFilter filter = new FilenameFilter() {
            public boolean accept(File dir, String file) {
                return file.endsWith(".in");
                }
            };
        for (File inf : dir.listFiles(filter)) {
            File dest = new File(inf.getParent(), inf.getName().substring(0, inf.getName().length()-3));
            FileUtils.copyFile(inf, dest);
        }
    }


    private void deleteGeneratedFiles()
    {
        File dir = new File(RESOURCE_DIR);
        FilenameFilter filter = new FilenameFilter() {
            public boolean accept(File dir, String file) {
                return !(file.endsWith(".in"));
                }
            };
        for (File file : dir.listFiles(filter)) {
            file.delete();
        }
    }


    @Before
    public void setup() throws IOException
    {
        copyInFiles();
        setupUS();
    }


    @After
    public void tearDown()
    {
        deleteGeneratedFiles();
    }



    @Test
    public void testSetup() throws IOException
    {
        assertEquals(NUM_USERS_HTPASSWD, htpwdUserService.getNumberUsers());
    }


    @Test
    public void testAuthenticate()
    {
        MS.put(KEY_SUPPORT_PLAINTEXT_PWD, "true");
        UserModel user = htpwdUserService.authenticate("user1", "pass1".toCharArray());
        assertNotNull(user);
        assertEquals("user1", user.username);

        user = htpwdUserService.authenticate("user2", "pass2".toCharArray());
        assertNotNull(user);
        assertEquals("user2", user.username);

        // Test different encryptions
        user = htpwdUserService.authenticate("plain", "passWord".toCharArray());
        assertNotNull(user);
        assertEquals("plain", user.username);

        MS.put(KEY_SUPPORT_PLAINTEXT_PWD, "false");
        user = htpwdUserService.authenticate("crypt", "password".toCharArray());
        assertNotNull(user);
        assertEquals("crypt", user.username);

        user = htpwdUserService.authenticate("md5", "password".toCharArray());
        assertNotNull(user);
        assertEquals("md5", user.username);

        user = htpwdUserService.authenticate("sha", "password".toCharArray());
        assertNotNull(user);
        assertEquals("sha", user.username);


        // Test leading and trailing whitespace
        user = htpwdUserService.authenticate("trailing", "whitespace".toCharArray());
        assertNotNull(user);
        assertEquals("trailing", user.username);

        user = htpwdUserService.authenticate("tabbed", "frontAndBack".toCharArray());
        assertNotNull(user);
        assertEquals("tabbed", user.username);

        user = htpwdUserService.authenticate("leading", "whitespace".toCharArray());
        assertNotNull(user);
        assertEquals("leading", user.username);


        // Test local account
        user = htpwdUserService.authenticate("admin", "admin".toCharArray());
        assertNotNull(user);
        assertEquals("admin", user.username);
    }


    @Test
    public void testAttributes()
    {
        MS.put(KEY_SUPPORT_PLAINTEXT_PWD, "true");
        UserModel user = htpwdUserService.authenticate("user1", "pass1".toCharArray());
        assertNotNull(user);
        assertEquals("El Capitan", user.displayName);
        assertEquals("cheffe@example.com", user.emailAddress);
        assertTrue(user.canAdmin);

        user = htpwdUserService.authenticate("user2", "pass2".toCharArray());
        assertNotNull(user);
        assertEquals("User Two", user.displayName);
        assertTrue(user.canCreate);
        assertTrue(user.canFork);


        user = htpwdUserService.authenticate("admin", "admin".toCharArray());
        assertNotNull(user);
        assertTrue(user.canAdmin);

        user = htpwdUserService.authenticate("staylocal", "localUser".toCharArray());
        assertNotNull(user);
        assertEquals("Local User", user.displayName);
        assertFalse(user.canCreate);
        assertFalse(user.canFork);
        assertFalse(user.canAdmin);
    }


    @Test
    public void testAuthenticateDenied()
    {
        UserModel user = null;
        MS.put(KEY_SUPPORT_PLAINTEXT_PWD, "true");
        user = htpwdUserService.authenticate("user1", "".toCharArray());
        assertNull("User 'user1' falsely authenticated.", user);

        user = htpwdUserService.authenticate("user1", "pass2".toCharArray());
        assertNull("User 'user1' falsely authenticated.", user);

        user = htpwdUserService.authenticate("user2", "lalala".toCharArray());
        assertNull("User 'user2' falsely authenticated.", user);


        user = htpwdUserService.authenticate("user3", "disabled".toCharArray());
        assertNull("User 'user3' falsely authenticated.", user);

        user = htpwdUserService.authenticate("user4", "disabled".toCharArray());
        assertNull("User 'user4' falsely authenticated.", user);


        user = htpwdUserService.authenticate("plain", "text".toCharArray());
        assertNull("User 'plain' falsely authenticated.", user);

        user = htpwdUserService.authenticate("plain", "password".toCharArray());
        assertNull("User 'plain' falsely authenticated.", user);


        MS.put(KEY_SUPPORT_PLAINTEXT_PWD, "false");

        user = htpwdUserService.authenticate("crypt", "".toCharArray());
        assertNull("User 'cyrpt' falsely authenticated.", user);

        user = htpwdUserService.authenticate("crypt", "passwd".toCharArray());
        assertNull("User 'crypt' falsely authenticated.", user);

        user = htpwdUserService.authenticate("md5", "".toCharArray());
        assertNull("User 'md5' falsely authenticated.", user);

        user = htpwdUserService.authenticate("md5", "pwd".toCharArray());
        assertNull("User 'md5' falsely authenticated.", user);

        user = htpwdUserService.authenticate("sha", "".toCharArray());
        assertNull("User 'sha' falsely authenticated.", user);

        user = htpwdUserService.authenticate("sha", "letmein".toCharArray());
        assertNull("User 'sha' falsely authenticated.", user);


        user = htpwdUserService.authenticate("  tabbed", "frontAndBack".toCharArray());
        assertNull("User 'tabbed' falsely authenticated.", user);

        user = htpwdUserService.authenticate("    leading", "whitespace".toCharArray());
        assertNull("User 'leading' falsely authenticated.", user);
    }


    @Test
    public void testNewLocalAccount()
    {
        UserModel newUser = new UserModel("newlocal");
        newUser.displayName = "Local User 2";
        newUser.password = StringUtils.MD5_TYPE + StringUtils.getMD5("localPwd2");
        assertTrue("Failed to add local account.", htpwdUserService.updateUserModel(newUser));

        UserModel localAccount = htpwdUserService.authenticate(newUser.username, "localPwd2".toCharArray());
        assertNotNull(localAccount);
        assertEquals(newUser, localAccount);

        localAccount = htpwdUserService.authenticate(newUser.username, "localPwd2".toCharArray());
        assertNotNull(localAccount);
        assertEquals(newUser, localAccount);

        assertTrue("Failed to delete local account.", htpwdUserService.deleteUser(localAccount.username));
        assertNull(htpwdUserService.authenticate(newUser.username, "localPwd2".toCharArray()));
    }


    @Test
    public void testCleartextIntrusion()
    {
        MS.put(KEY_SUPPORT_PLAINTEXT_PWD, "true");
        assertNull(htpwdUserService.authenticate("md5", "$apr1$qAGGNfli$sAn14mn.WKId/3EQS7KSX0".toCharArray()));
        assertNull(htpwdUserService.authenticate("sha", "{SHA}W6ph5Mm5Pz8GgiULbPgzG37mj9g=".toCharArray()));

        assertNull(htpwdUserService.authenticate("user1", "#externalAccount".toCharArray()));

        MS.put(KEY_SUPPORT_PLAINTEXT_PWD, "false");
        assertNull(htpwdUserService.authenticate("md5", "$apr1$qAGGNfli$sAn14mn.WKId/3EQS7KSX0".toCharArray()));
        assertNull(htpwdUserService.authenticate("sha", "{SHA}W6ph5Mm5Pz8GgiULbPgzG37mj9g=".toCharArray()));

        assertNull(htpwdUserService.authenticate("user1", "#externalAccount".toCharArray()));
    }


    @Test
    public void testCryptVsPlaintext()
    {
        MS.put(KEY_SUPPORT_PLAINTEXT_PWD, "false");
        assertNull(htpwdUserService.authenticate("crypt", "6TmlbxqZ2kBIA".toCharArray()));
        assertNotNull(htpwdUserService.authenticate("crypt", "password".toCharArray()));

        MS.put(KEY_SUPPORT_PLAINTEXT_PWD, "true");
        assertNotNull(htpwdUserService.authenticate("crypt", "6TmlbxqZ2kBIA".toCharArray()));
        assertNull(htpwdUserService.authenticate("crypt", "password".toCharArray()));
    }


    /*
     * Test case: User exists in user.conf with a local password and in htpasswd with an external password.
     * If overrideLocalAuthentication is false, the local account takes precedence and is never updated.
     */
    @Test
    public void testPreparedAccountPreferLocal() throws IOException
    {
        setupUS(false);

        UserModel user = htpwdUserService.authenticate("leaderred", "localPassword".toCharArray());
        assertNotNull(user);
        assertEquals("leaderred", user.getName());

        user = htpwdUserService.authenticate("leaderred", "localPassword".toCharArray());
        assertNotNull(user);
        assertEquals("leaderred", user.getName());

        user = htpwdUserService.authenticate("leaderred", "externalPassword".toCharArray());
        assertNull(user);

        user = htpwdUserService.authenticate("staylocal", "localUser".toCharArray());
        assertNotNull(user);
        assertEquals("staylocal", user.getName());


        deleteGeneratedFiles();
        copyInFiles();
        setupUS(false);

        user = htpwdUserService.authenticate("leaderred", "externalPassword".toCharArray());
        assertNull(user);

        user = htpwdUserService.authenticate("leaderred", "localPassword".toCharArray());
        assertNotNull(user);
        assertEquals("leaderred", user.getName());

        user = htpwdUserService.authenticate("leaderred", "localPassword".toCharArray());
        assertNotNull(user);
        assertEquals("leaderred", user.getName());

        user = htpwdUserService.authenticate("staylocal", "localUser".toCharArray());
        assertNotNull(user);
        assertEquals("staylocal", user.getName());
    }


    /*
     * Test case: User exists in user.conf with a local password and in htpasswd with an external password.
     * If overrideLocalAuthentication is true, the external account takes precedence,
     * the initial local password is never used and discarded.
     */
    @Test
    public void testPreparedAccountPreferExternal() throws IOException
    {
        setupUS(true);

        UserModel user = htpwdUserService.authenticate("leaderred", "externalPassword".toCharArray());
        assertNotNull(user);
        assertEquals("leaderred", user.getName());

        user = htpwdUserService.authenticate("leaderred", "externalPassword".toCharArray());
        assertNotNull(user);
        assertEquals("leaderred", user.getName());

        user = htpwdUserService.authenticate("leaderred", "localPassword".toCharArray());
        assertNull(user);

        user = htpwdUserService.authenticate("staylocal", "localUser".toCharArray());
        assertNotNull(user);
        assertEquals("staylocal", user.getName());


        deleteGeneratedFiles();
        copyInFiles();
        setupUS(true);


        user = htpwdUserService.authenticate("leaderred", "localPassword".toCharArray());
        assertNull(user);

        user = htpwdUserService.authenticate("leaderred", "externalPassword".toCharArray());
        assertNotNull(user);
        assertEquals("leaderred", user.getName());

        user = htpwdUserService.authenticate("leaderred", "externalPassword".toCharArray());
        assertNotNull(user);
        assertEquals("leaderred", user.getName());

        user = htpwdUserService.authenticate("staylocal", "localUser".toCharArray());
        assertNotNull(user);
        assertEquals("staylocal", user.getName());

        // Make sure no authentication by using the string constant for external accounts is possible.
        user = htpwdUserService.authenticate("leaderred", "#externalAccount".toCharArray());
        assertNull(user);
    }


    /*
     * Test case: User exists in user.conf with a local password and in htpasswd with an external password.
     * If overrideLocalAuthentication is true, the external account takes precedence,
     * the initial local password is never used and discarded.
     */
    @Test
    public void testPreparedAccountChangeSetting() throws IOException
    {
        getSettings(false);

        UserModel user = htpwdUserService.authenticate("leaderred", "localPassword".toCharArray());
        assertNotNull(user);
        assertEquals("leaderred", user.getName());

        user = htpwdUserService.authenticate("leaderred", "externalPassword".toCharArray());
        assertNull(user);

        user = htpwdUserService.authenticate("staylocal", "localUser".toCharArray());
        assertNotNull(user);
        assertEquals("staylocal", user.getName());


        getSettings(true);


        user = htpwdUserService.authenticate("leaderred", "localPassword".toCharArray());
        assertNull(user);

        user = htpwdUserService.authenticate("leaderred", "externalPassword".toCharArray());
        assertNotNull(user);
        assertEquals("leaderred", user.getName());

        user = htpwdUserService.authenticate("staylocal", "localUser".toCharArray());
        assertNotNull(user);
        assertEquals("staylocal", user.getName());

        // Make sure no authentication by using the string constant for external accounts is possible.
        user = htpwdUserService.authenticate("leaderred", "#externalAccount".toCharArray());
        assertNull(user);


        getSettings(false);
        // The preference is now back to local accounts but since the prepared account got switched
        // to an external account, it will stay this way.

        user = htpwdUserService.authenticate("leaderred", "localPassword".toCharArray());
        assertNull(user);

        user = htpwdUserService.authenticate("leaderred", "externalPassword".toCharArray());
        assertNotNull(user);
        assertEquals("leaderred", user.getName());

        user = htpwdUserService.authenticate("staylocal", "localUser".toCharArray());
        assertNotNull(user);
        assertEquals("staylocal", user.getName());

        // Make sure no authentication by using the string constant for external accounts is possible.
        user = htpwdUserService.authenticate("leaderred", "#externalAccount".toCharArray());
        assertNull(user);
    }

}
