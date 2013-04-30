package com.gitblit.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static com.gitblit.GitBlitServer.getConfigFile;
import java.io.File;
import org.junit.Test;

/**
 * Provides the tests for the server.
 * 
 * @author Bret K. Ikehara
 */
public class GitBlitServerTest {
  
  /**
   * Tests whether the store file were read correctly.
   */
  @Test
  public void testGetStoreFile() {
    
    File defaultFolder = new File(".");
    String defaultFileName = "NOTICE";
    
    // gets the license file.
    String fileName = "LICENSE";
    File storeFile = getConfigFile(fileName, defaultFolder, defaultFileName);
    assertEquals("License file", fileName, storeFile.getName());
    assertTrue("License exists", storeFile.exists());
    
    // gets the license file.
    fileName = "LICENSE.txt";
    assertFalse("License.txt does not exist", (new File(fileName)).exists());
    storeFile = getConfigFile(fileName, defaultFolder, defaultFileName);
    assertEquals("Notice file", defaultFileName, storeFile.getName());
    assertTrue("Notice exists", storeFile.exists());
  }
}
