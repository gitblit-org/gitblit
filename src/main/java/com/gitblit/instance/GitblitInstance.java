package com.gitblit.instance;

import com.gitblit.IStoredSettings;
import com.gitblit.manager.IRuntimeManager;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class GitblitInstance
{
    private final static String STATS_URL = "https://instats.gitblit.dev/hiitsme/";

    private IRuntimeManager runtimeManager;

    private String instanceId;

    private GitblitInstanceReport report;



    /**
     * Initialize the Gitblit instance reporting system.
     *
     * This will gather the static and dynamic statistics about the running
     * instance, so that they can be reported.
     *
     * @param runtimeManager
     *            The runtime manager is used to determine the type of instance
     *            as well as for some other settings and data.
     */
    public void init(IRuntimeManager runtimeManager) {
        this.runtimeManager = runtimeManager;

        // Initialize ID
        GitblitInstanceId instanceId = new GitblitInstanceId(runtimeManager.getBaseFolder());
        this.instanceId = instanceId.getId().toString();

        GitblitInstanceStat instanceStat;

        if (runtimeManager.getSettings().hasSettings("container.dockerfileVersion")) {
            instanceStat = new GitblitInstanceStat(GitblitInstanceStat.GitblitInstanceType.DOCKER);
        }
        else if (runtimeManager.getStatus().isGO){
            instanceStat = new GitblitInstanceStat(GitblitInstanceStat.GitblitInstanceType.GO);
        }
        else {
            instanceStat = new GitblitInstanceStat(GitblitInstanceStat.GitblitInstanceType.WAR);
        }

        instanceStat.init(runtimeManager.getStatus());

        this.report = new GitblitInstanceReport(this.instanceId, instanceStat);
    }


    public void start()
    {
        if (shouldRunReports()) {
            startReports();
        }
    }


    /**
     * Determine if the reporting task should run.
     *
     * We do not want to report anything, i.e. the reporting task to run,
     * if we are running unit tests or integration tests.
     * Instance reports should only be sent for production instances or released versions.
     * Therefore we also check if the Gitblit version is a SNAPSHOT version,
     * or if the docker image is not a release version, when running from a docker image.
     * A docker image running under GOSS should also not report anything.
     */
    boolean shouldRunReports()
    {
        // We can only run reports when we have been initialized
        if (this.report == null || this.runtimeManager == null) {
            return false;
        }

        // Check if we are running in a test environment
        IStoredSettings settings = this.runtimeManager.getSettings();
        if (! settings.getString("gitblit.testReportingURL", "").isEmpty()) {
            // Force reporting to run overriding any test settings
            return true;
        }
        if (settings.getBoolean("gitblit.testRun", false)) {
            return false;
        }

        // Check if we are running a SNAPSHOT version
        if (this.runtimeManager.getStatus().version.endsWith("SNAPSHOT")) {
            return false;
        }

        if (this.report.instanceStat.instanceType == GitblitInstanceStat.GitblitInstanceType.DOCKER) {
            // Check if we are running a docker image that is not a release version
            if (! settings.getString("container.imageType", "").equals("release")) {
                return false;
            }

            // Check if we are running a docker image under GOSS
            if (System.getenv("GITBLIT_GOSS_TEST") != null) {
                return false;
            }
        }

        return true;
    }


    /**
     * Start the reporting task.
     *
     * This will start a thread that runs once a day and sends the instance
     * report to the popularity report server.
     */
    private void startReports()
    {
        this.executor = Executors.newSingleThreadScheduledExecutor();

        String baseUrl = STATS_URL;
        int delay = 24;
        int period = 24 * 60; // 24 hours in minutes
        TimeUnit unit = TimeUnit.MINUTES;

        this.executor.scheduleAtFixedRate(new Runnable()
        {
            @Override
            public void run()
            {
                sendMyStats(baseUrl + instanceId);
            }
        }, delay, period, unit);
    }

    /**
     * Send the instance report to the popularity report server.
     *
     * This will send a JSON object to the server with the instance report.
     *
     * @param server
     *            The URL to send the report to.
     */
    private void sendMyStats(String server)
    {
    }

}
