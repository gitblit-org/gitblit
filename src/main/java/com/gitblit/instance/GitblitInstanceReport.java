package com.gitblit.instance;

import com.google.gson.annotations.SerializedName;

import java.text.SimpleDateFormat;
import java.util.TimeZone;

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
    private final String startTs;
    private String lpingTs;

    final GitblitInstanceStat instanceStat;

    GitblitInstanceReport(String instanceId, GitblitInstanceStat instanceStat)
    {
        this.instanceId = instanceId;
        this.instanceStat = instanceStat;

        // Convert the timestamp taken from instanceStat to a string in the format "yyyy-MM-dd'T'HHmmssZ" so
        // it can be used better in a file name. It is replicated here so that it can be directly used by the receiver.
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'");
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        this.startTs = dateFormat.format(instanceStat.startTs);
    }

    GitblitInstanceReport fromNow()
    {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'");
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        this.lpingTs = dateFormat.format(System.currentTimeMillis());
        return this;
    }
}
