package com.gitblit;

import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;

public class StoredUserConfigTest
{
    private static File file;

    @Before
    public void setup()
    {
        file = new File("./suc-test.conf");
        file.delete();
    }

    @After
    public void teardown()
    {
        file.delete();
    }



    @Test
    public void testSection() throws Exception
    {
        StoredUserConfig config = new StoredUserConfig(file);
        config.setString("USER", "norman", "key", "value");
        config.setString("USER", "admin", "displayName", "marusha");
        config.setString("USER", null, "role", "none");

        config.setString("TEAM", "admin", "role", "admin");
        config.setString("TEAM", "ci", "email", "ci@example.com");
        config.setString("TEAM", null, "displayName", "noone");

        config.save();

        StoredConfig cfg = new FileBasedConfig(file, FS.detect());
        cfg.load();
        assertEquals("value", cfg.getString("USER", "norman", "key"));
        assertEquals("marusha", cfg.getString("USER", "admin", "displayName"));
        assertEquals("none", cfg.getString("USER", null, "role"));

        assertEquals("admin", cfg.getString("TEAM", "admin", "role"));
        assertEquals("ci@example.com", cfg.getString("TEAM", "ci", "email"));
        assertEquals("noone", cfg.getString("TEAM", null, "displayName"));
    }


    @Test
    public void testStringFields() throws Exception
    {
        StoredUserConfig config = new StoredUserConfig(file);
        config.setString("USER", "admin", "password", "secret");
        config.setString("USER", "admin", "displayName", "marusha");
        config.setString("USER", "admin", "email", "name@example.com");

        config.setString("USER", "other", "password", "password");
        config.setString("USER", "other", "displayName", "mama");
        config.setString("USER", "other", "email", "other@example.com");
        config.setString("USER", "other", "repository", "RW+:repo1");
        config.setString("USER", "other", "repository", "RW+:repo2");

        config.setString("USER", null, "displayName", "default");

        config.save();

        StoredConfig cfg = new FileBasedConfig(file, FS.detect());
        cfg.load();
        assertEquals("secret", cfg.getString("USER", "admin", "password"));
        assertEquals("marusha", cfg.getString("USER", "admin", "displayName"));
        assertEquals("name@example.com", cfg.getString("USER", "admin", "email"));

        assertEquals("password", cfg.getString("USER", "other", "password"));
        assertEquals("mama", cfg.getString("USER", "other", "displayName"));
        assertEquals("other@example.com", cfg.getString("USER", "other", "email"));

        String[] stringList = cfg.getStringList("USER", "other", "repository");
        assertNotNull(stringList);
        assertEquals(2, stringList.length);
        int i = 0;
        for (String s : stringList) {
            if (s.equalsIgnoreCase("RW+:repo1")) i += 1;
            else if (s.equalsIgnoreCase("RW+:repo2")) i += 2;
        }
        assertEquals("Not all repository strings found", 3, i);

        assertEquals("default", cfg.getString("USER", null, "displayName"));
    }


    @Test
    public void testBooleanFields() throws Exception
    {
        StoredUserConfig config = new StoredUserConfig(file);
        config.setBoolean("USER", "admin", "emailMeOnMyTicketChanges", true);
        config.setBoolean("USER", "master", "emailMeOnMyTicketChanges", false);
        config.setBoolean("TEAM", "admin", "excludeFromFederation", true);
        config.setBoolean("USER", null, "excludeFromFederation", false);

        config.save();

        StoredConfig cfg = new FileBasedConfig(file, FS.detect());
        cfg.load();
        assertTrue(cfg.getBoolean("USER", "admin", "emailMeOnMyTicketChanges", false));
        assertFalse(cfg.getBoolean("USER", "master", "emailMeOnMyTicketChanges", true));
        assertTrue(cfg.getBoolean("TEAM", "admin", "excludeFromFederation", false));
        assertFalse(cfg.getBoolean("USER", null, "excludeFromFederation", true));
    }


    @Test
    public void testHashEscape() throws Exception
    {
        StoredUserConfig config = new StoredUserConfig(file);
        config.setString("USER", "admin", "role", "#admin");

        config.setString("USER", "other", "role", "#none");
        config.setString("USER", "other", "displayName", "big#");
        config.setString("USER", "other", "email", "user#name@home.de");

        config.save();

        StoredConfig cfg = new FileBasedConfig(file, FS.detect());
        cfg.load();
        assertEquals("#admin", cfg.getString("USER", "admin", "role"));
        assertEquals("#none", cfg.getString("USER", "other", "role"));
        assertEquals("big#", cfg.getString("USER", "other", "displayName"));
        assertEquals("user#name@home.de", cfg.getString("USER", "other", "email"));
    }


    @Test
    public void testCtrlEscape() throws Exception
    {
        StoredUserConfig config = new StoredUserConfig(file);
        config.setString("USER", "name", "password", "bing\bbong");
        config.setString("USER", "name", "role", "domain\\admin");
        config.setString("USER", "name", "displayName", "horny\n\telephant");
        config.setString("USER", "name", "org", "\tbig\tblue");
        config.setString("USER", "name", "unit", "the end\n");

        config.setString("USER", null, "unit", "the\ndefault");

        config.save();

        StoredConfig cfg = new FileBasedConfig(file, FS.detect());
        cfg.load();
        assertEquals("bing\bbong", cfg.getString("USER", "name", "password"));
        assertEquals("domain\\admin", cfg.getString("USER", "name", "role"));
        assertEquals("horny\n\telephant", cfg.getString("USER", "name", "displayName"));
        assertEquals("\tbig\tblue", cfg.getString("USER", "name", "org"));
        assertEquals("the end\n", cfg.getString("USER", "name", "unit"));

        assertEquals("the\ndefault", cfg.getString("USER", null, "unit"));
    }


    @Test
    public void testQuoteEscape() throws Exception
    {
        StoredUserConfig config = new StoredUserConfig(file);
        config.setString("USER", "dude", "password", "going\"places");
        config.setString("USER", "dude", "role", "\"dude\"");
        config.setString("USER", "dude", "displayName", "John \"The Dude\" Lebowski");
        config.setString("USER", "dude", "repo", "\"front matter");
        config.setString("USER", "dude", "peepo", "leadout\"");

        config.save();

        StoredConfig cfg = new FileBasedConfig(file, FS.detect());
        cfg.load();
        assertEquals("going\"places", cfg.getString("USER", "dude", "password"));
        assertEquals("\"dude\"", cfg.getString("USER", "dude", "role"));
        assertEquals("John \"The Dude\" Lebowski", cfg.getString("USER", "dude", "displayName"));
        assertEquals("\"front matter", cfg.getString("USER", "dude", "repo"));
        assertEquals("leadout\"", cfg.getString("USER", "dude", "peepo"));
    }


    @Test
    public void testUTF8() throws Exception
    {
        StoredUserConfig config = new StoredUserConfig(file);
        config.setString("USER", "ming", "password", "一\t二\n三");
        config.setString("USER", "ming", "displayName", "白老鼠");
        config.setString("USER", "ming", "peepo", "Mickey \"白老鼠\" Whitfield");

        config.save();

        StoredConfig cfg = new FileBasedConfig(file, FS.detect());
        cfg.load();
        assertEquals("一\t二\n三", cfg.getString("USER", "ming", "password"));
        assertEquals("白老鼠", cfg.getString("USER", "ming", "displayName"));
        assertEquals("Mickey \"白老鼠\" Whitfield", cfg.getString("USER", "ming", "peepo"));
    }

}
