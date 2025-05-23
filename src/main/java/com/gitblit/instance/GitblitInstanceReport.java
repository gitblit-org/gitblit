package com.gitblit.instance;

import com.google.gson.annotations.SerializedName;

/**
 * GitblitInstanceReport collects the static and dynamic statistics about a running
 * Gitblit instance, pairs it with a report version and instance id.
 * This can then be send to the popularity report server.
 *
 */
class GitblitInstanceReport
{
    private final int reportVersion = 1;
    @SerializedName("instance")
    private final String instanceId;
    final GitblitInstanceStat instanceStat;

    GitblitInstanceReport(String instanceId, GitblitInstanceStat instanceStat)
    {
        this.instanceId = instanceId;
        this.instanceStat = instanceStat;
    }
}
