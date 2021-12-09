package com.gitblit.utils;

import org.slf4j.Logger;

import java.io.File;

/**
 * The ContainerDetector tries to detect if the Gitblit instance
 * is running in a container, or directly on the (virtualized) OS.
 */
public class ContainerDetector
{
    private static Boolean inContainer;
    private static String detectedType = "";

    /**
     * Detect if this instance in running inside a container.
     *
     * @return true - if a container could be detected
     *         false - otherwise
     */
    public static boolean detect()
    {
        if (inContainer == null) {
            File proc = new File("/proc/1/cgroup");
            if (! proc.exists()) inContainer = Boolean.FALSE;
            else {
                String cgroups = FileUtils.readContent(proc, null);
                if (cgroups.contains("/docker")) {
                    inContainer = Boolean.TRUE;
                    detectedType = "Docker container";
                }
                else if (cgroups.contains("/ecs")) {
                    inContainer = Boolean.TRUE;
                    detectedType = "ECS container";
                }
                else if (cgroups.contains("/kubepod") || cgroups.contains("/kubepods")) {
                    inContainer = Boolean.TRUE;
                    detectedType = "Kubernetes pod";
                }
            }

            // Finally, if we still haven't found proof, it is probably not a container
            if (inContainer == null) inContainer = Boolean.FALSE;
        }

        return inContainer;
    }


    /**
     * Report to some output if a container was detected.
     *
     */
    public static void report(Logger logger, boolean onlyIfInContainer)
    {
        if (detect()) {
            String msg = "Running in a " + detectedType;
            if (logger == null) {
                System.out.println(msg);
            }
            else logger.info(msg);
        }
        else if (!onlyIfInContainer) {
            String msg = "Not detected to be running in a container";
            if (logger == null) {
                System.out.println(msg);
            }
            else logger.info(msg);

        }

    }
}
