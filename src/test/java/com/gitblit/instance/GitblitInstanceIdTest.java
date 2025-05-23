package com.gitblit.instance;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


public class GitblitInstanceIdTest
{
    @Rule
    public TemporaryFolder baseFolder = new TemporaryFolder();

    /**
     * Tests that the version nibble is set to 0x8.
     */
    @Test
    public void testUuidVersion() throws Exception
    {
        GitblitInstanceId id = new GitblitInstanceId();
        UUID uuid = id.getId();
        assertNotNull(uuid);
        long upper = uuid.getMostSignificantBits();
        assertEquals(0x0000000000008000L, (upper & 0x000000000000F000L));
    }

    /**
     * Tests that the variant nibble is set to 0x8.
     */
    @Test
    public void testUuidVariant() throws Exception
    {
        GitblitInstanceId id = new GitblitInstanceId();
        UUID uuid = id.getId();
        assertNotNull(uuid);
        long lower = uuid.getLeastSignificantBits();
        assertEquals(0x8000000000000000L, (lower & 0xF000000000000000L));
    }

    /**
     * Test that the first four bytes hold a timestamp in a newly generated id.
     */
    @Test
    public void testDatePart() throws Exception
    {
        GitblitInstanceId id = new GitblitInstanceId();
        UUID uuid = id.getId();
        assertNotNull(uuid);
        long upper = uuid.getMostSignificantBits();
        long ts = System.currentTimeMillis();
        assertEquals("Date part of UUID does not equal current date/time.", ts >> 2*8, upper >> 4*8);
    }



    /**
     * Test that a new id is generated and stored in a file, when none existed.
     */
    @Test
    public void testStoreId() throws Exception
    {
        GitblitInstanceId id = new GitblitInstanceId(baseFolder.getRoot());
        UUID uuid = id.getId();
        assertNotNull(uuid);
        long lower = uuid.getLeastSignificantBits();
        assertEquals(0x8000000000000000L, (lower & 0xF000000000000000L));
        long upper = uuid.getMostSignificantBits();
        assertEquals(0x0000000000008000L, (upper & 0x000000000000F000L));

        File idFile = new File(baseFolder.getRoot(), GitblitInstanceId.STORAGE_FILE);
        assertTrue("Id file was not created", idFile.exists());

        String string = Files.readAllLines(idFile.toPath()).get(0);
        try {
            UUID uuidFromFile = UUID.fromString(string);
            assertEquals("Returned id and id read from file are not equal.", uuid, uuidFromFile);
        } catch (IllegalArgumentException e) {
            fail("UUID read from file is not valid: " + string);
        }
    }


    /**
     * Test that a new id is generated and stored in a file, when none existed.
     */
    @Test
    public void testStoreIdNonexistingFolder() throws Exception
    {
        File baseSubFolder = new File(baseFolder.getRoot(), "nonexisting");

        GitblitInstanceId id = new GitblitInstanceId(baseSubFolder);
        UUID uuid = id.getId();
        assertNotNull(uuid);
        long lower = uuid.getLeastSignificantBits();
        assertEquals(0x8000000000000000L, (lower & 0xF000000000000000L));
        long upper = uuid.getMostSignificantBits();
        assertEquals(0x0000000000008000L, (upper & 0x000000000000F000L));

        File idFile = new File(baseSubFolder, GitblitInstanceId.STORAGE_FILE);
        assertTrue("Id file was not created", idFile.exists());

        String string = Files.readAllLines(idFile.toPath()).get(0);
        try {
            UUID uuidFromFile = UUID.fromString(string);
            assertEquals("Returned id and id read from file are not equal.", uuid, uuidFromFile);
        } catch (IllegalArgumentException e) {
            fail("UUID read from file is not valid: " + string);
        }
    }


    /**
     * Test that an existing id is read from an existing id file.
     */
    @Test
    public void testReadId() throws Exception
    {
        File idFile = new File(baseFolder.getRoot(), GitblitInstanceId.STORAGE_FILE);
        String uuidString = "0196e409-c664-82f3-88f1-e963d16c7b8a";
        Files.write(idFile.toPath(), uuidString.getBytes());

        GitblitInstanceId id = new GitblitInstanceId(baseFolder.getRoot());
        UUID uuid = id.getId();
        assertNotNull(uuid);

        UUID refUuid = UUID.fromString(uuidString);
        assertEquals("Returned id is not equal to reference id", refUuid, uuid);
    }


    /**
     * Test reading id from a file with whitespace
     */
    @Test
    public void testReadIdWhitespace() throws Exception
    {
        File idFile = new File(baseFolder.getRoot(), GitblitInstanceId.STORAGE_FILE);
        String uuidString = "0196e409-c664-82f3-88f1-e963d16c7b8a";
        String fileString = "\n  " + uuidString + "  \n  \n";
        Files.write(idFile.toPath(), fileString.getBytes());

        GitblitInstanceId id = new GitblitInstanceId(baseFolder.getRoot());
        UUID uuid = id.getId();
        assertNotNull(uuid);

        UUID refUuid = UUID.fromString(uuidString);
        assertEquals("Returned id is not equal to reference id", refUuid, uuid);
    }

}