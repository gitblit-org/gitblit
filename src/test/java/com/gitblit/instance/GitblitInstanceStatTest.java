package com.gitblit.instance;

import com.gitblit.Constants;
import com.gitblit.models.ServerStatus;
import org.junit.Before;
import org.junit.Test;

import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class GitblitInstanceStatTest
{

    protected GitblitInstanceStat instanceStat;
    protected ServerStatus serverStatus;

    @Before
    public void setUp() throws Exception
    {
        instanceStat = new GitblitInstanceStat();
        serverStatus = new ServerStatus();
        instanceStat.init(serverStatus);
    }


    @Test
    public void testGetVersion()
    {
        String version = instanceStat.version;
        assertNotNull(version);
        assertFalse(version.isEmpty());
        assertEquals(Constants.getVersion(), version);
    }

    @Test
    public void testGetStartTs()
    {
        Date date = instanceStat.startTs;
        assertNotNull(date);
        assertEquals(serverStatus.bootDate, date);
    }

    @Test
    public void testGetType()
    {
        String type = instanceStat.instanceType.name();
        assertNotNull(type);
        assertFalse(type.isEmpty());
        assertEquals("WAR", type);
    }

    @Test
    public void testGetOS()
    {
        String os = instanceStat.os;

        String oslc = System.getProperty("os.name").toLowerCase();

        if (oslc.contains("windows")) {
            assertEquals("Windows", os);
        }
        else if (oslc.contains("linux")) {
            assertEquals("Linux", os);
        }
        else if (oslc.contains("mac")) {
            assertEquals("macOS", os);
        }
    }

    @Test
    public void testGetOSName()
    {
        String name = instanceStat.osName;
        assertNotNull(name);
        assertFalse(name.isEmpty());
        assertEquals(System.getProperty("os.name"), name);
    }

    @Test
    public void testGetOSVersion()
    {
        String version = instanceStat.osVersion;
        assertNotNull(version);
        assertFalse(version.isEmpty());
        assertEquals(System.getProperty("os.version"), version);
    }

    @Test
    public void testGetOSArch()
    {
        String arch = instanceStat.osArch;
        assertNotNull(arch);
        assertFalse(arch.isEmpty());
        assertEquals(System.getProperty("os.arch"), arch);
    }

    @Test
    public void testGetJavaVersion()
    {
        String version = instanceStat.javaVersion;
        assertNotNull(version);
        assertFalse(version.isEmpty());
        assertEquals(System.getProperty("java.version"), version);
    }

    @Test
    public void testGetJavaVendor()
    {
        String vendor = instanceStat.javaVendor;
        assertNotNull(vendor);
        assertFalse(vendor.isEmpty());
        assertEquals(System.getProperty("java.vendor"), vendor);
    }

    @Test
    public void testGetJavaRuntimeVersion()
    {
        String rt = instanceStat.javaRuntimeVersion;
        assertNotNull(rt);
        assertFalse(rt.isEmpty());
        assertEquals(System.getProperty("java.runtime.version"), rt);
    }

    @Test
    public void testGetJavaRuntimeName()
    {
        String rt = instanceStat.javaRuntimeName;
        assertNotNull(rt);
        assertFalse(rt.isEmpty());
        assertEquals(System.getProperty("java.runtime.name"), rt);
    }

    @Test
    public void testGetJavaVmVersion()
    {
        String vm = instanceStat.javaVmVersion;
        assertNotNull(vm);
        assertFalse(vm.isEmpty());
        assertEquals(System.getProperty("java.vm.version"), vm);
    }

    @Test
    public void testGetJavaVmName()
    {
        String vm = instanceStat.javaVmName;
        assertNotNull(vm);
        assertFalse(vm.isEmpty());
        assertEquals(System.getProperty("java.vm.name"), vm);
    }

    @Test
    public void testGetMaxMem()
    {
        long maxMem = instanceStat.maxMem;
        assertTrue(maxMem > 0);
        assertEquals(Runtime.getRuntime().maxMemory(), maxMem);
    }

    @Test
    public void testToString()
    {
        String str = instanceStat.toString();
        assertNotNull(str);
        assertFalse(str.isEmpty());
        assertTrue(str.contains("GitblitInstanceStat"));
        assertTrue(str.contains("version"));
        assertTrue(str.contains("instanceType"));
        assertTrue(str.contains("os"));
        assertTrue(str.contains("osName"));
        assertTrue(str.contains("osVersion"));
        assertTrue(str.contains("osArch"));
        assertTrue(str.contains("javaVersion"));
        assertTrue(str.contains("javaVendor"));
        assertTrue(str.contains("javaRuntimeVersion"));
        assertTrue(str.contains("javaRuntimeName"));
        assertTrue(str.contains("javaVmVersion"));
        assertTrue(str.contains("javaVmName"));

    }
}
