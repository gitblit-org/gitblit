package com.gitblit.instance;

import com.gitblit.manager.IRuntimeManager;

import com.gitblit.models.ServerStatus;
import com.gitblit.tests.mock.MockRuntimeManager;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;



public class GitblitInstanceTest
{
    @Test
    public void testShouldNotReportUnintialized()
    {
        GitblitInstance instance = new GitblitInstance();
        assertFalse(instance.shouldRunReports());
    }

    @Test
    public void testShouldNotReportInTests()
    {
        GitblitInstance instance = new GitblitInstance();
        instance.init(new MockRuntimeManager());
        assertFalse(instance.shouldRunReports());
    }

    @Test
    public void testShouldNotReportInSnapshotVersion()
    {
        GitblitInstance instance = new GitblitInstance();
        IRuntimeManager runtimeManager = new MockRuntimeManager();
        runtimeManager.getSettings().overrideSetting("gitblit.testRun", "false");
        instance.init(runtimeManager);
        assertFalse(instance.shouldRunReports());
    }

    @Test
    public void testShouldReportIfForced()
    {
        GitblitInstance instance = new GitblitInstance();
        IRuntimeManager runtimeManager = new MockRuntimeManager();
        runtimeManager.getSettings().overrideSetting("gitblit.testRunReporting", "true");
        instance.init(runtimeManager);
        assertTrue(instance.shouldRunReports());
    }

    @Test
    public void testShouldReportInReleaseVersion()
    {
        ServerStatus serverStatus = new ServerStatus("1.10.123");
        MockRuntimeManager runtimeManager = new MockRuntimeManager();
        runtimeManager.setStatus(serverStatus);
        runtimeManager.getSettings().overrideSetting("gitblit.testRun", "false");

        GitblitInstance instance = new GitblitInstance();
        instance.init(runtimeManager);
        assertTrue(instance.shouldRunReports());
    }

}
