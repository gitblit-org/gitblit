package com.gitblit.instance;

import com.gitblit.models.ServerStatus;

import java.util.Date;

/**
 * GitblitInstanceStat collects the static information about a Gitblit instance,
 * such as its version, type, operating system and other static data.
 *
 */
class GitblitInstanceStat
{

    enum GitblitInstanceType {
        GO,
        WAR,
        EXPRESS,
        DOCKER
    }

    final GitblitInstanceType instanceType;

    String version;
    Date startTs;
    String os;
    String osName;
    String osVersion;
    String osArch;
    String javaVersion;
    String javaVendor;
    String javaRuntimeVersion;
    String javaRuntimeName;
    String javaVmVersion;
    String javaVmName;
    long maxMem;


    GitblitInstanceStat()
    {
        this.instanceType = GitblitInstanceType.WAR;
        initOS();
        initJava();
    }

    GitblitInstanceStat(GitblitInstanceType instanceType)
    {
        this.instanceType = instanceType;
        initOS();
        initJava();
    }


    GitblitInstanceStat init(ServerStatus serverStatus)
    {
        this.version = serverStatus.version;
        this.startTs = serverStatus.bootDate;

        this.maxMem = serverStatus.heapMaximum;

        return this;
    }


    void initOS()
    {
        String os = System.getProperty("os.name");
        if (os == null) {
            this.os = "Unknown";
        } else {
            String oslc = os.toLowerCase();
            if (oslc.contains("windows")) {
                this.os = "Windows";
            } else if (oslc.contains("linux")) {
                this.os = "Linux";
            } else if (oslc.contains("mac") || oslc.contains("darwin")) {
                this.os = "macOS";
            } else if (oslc.contains("bsd")) {
                this.os = "BSD";
            } else if (oslc.contains("solaris") || oslc.contains("sun") ||
                    oslc.contains("aix") || oslc.contains("hpux") || oslc.contains("unix")) {
                this.os = "Unix";
            } else {
                this.os = os;
            }
        }

        this.osName = System.getProperty("os.name");
        this.osVersion = System.getProperty("os.version");
        this.osArch = System.getProperty("os.arch");
    }

    void initJava()
    {
        this.javaVersion = System.getProperty("java.version");
        this.javaVendor = System.getProperty("java.vendor");
        this.javaRuntimeVersion = System.getProperty("java.runtime.version", "");
        this.javaRuntimeName = System.getProperty("java.runtime.name", "");
        this.javaVmVersion = System.getProperty("java.vm.version", "");
        this.javaVmName = System.getProperty("java.vm.name", "");
    }



    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("GitblitInstanceStat {")
          .append("\n  instanceType: ").append(instanceType)
          .append(",\n  version: ").append(version)
          .append(",\n  startTs: ").append(startTs)
          .append(",\n  os: ").append(os)
          .append(",\n  osName: ").append(osName)
          .append(",\n  osVersion: ").append(osVersion)
          .append(",\n  osArch: ").append(osArch)
          .append(",\n  javaVersion: ").append(javaVersion)
          .append(",\n  javaVendor: ").append(javaVendor)
          .append(",\n  javaRuntimeVersion: ").append(javaRuntimeVersion)
          .append(",\n  javaRuntimeName: ").append(javaRuntimeName)
          .append(",\n  javaVmVersion: ").append(javaVmVersion)
          .append(",\n  javaVmName: ").append(javaVmName)
          .append(",\n  maxMem: ").append(maxMem)
          .append("\n}");
        return sb.toString();
    }
}
