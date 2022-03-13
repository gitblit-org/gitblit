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

}
