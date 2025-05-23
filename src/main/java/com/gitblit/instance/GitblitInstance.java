package com.gitblit.instance;

import com.gitblit.manager.IRuntimeManager;

public class GitblitInstance
{

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
    }
}
