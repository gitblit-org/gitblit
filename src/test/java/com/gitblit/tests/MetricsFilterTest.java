package com.gitblit.tests;

import com.gitblit.servlet.MetricsFilter;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;


public class MetricsFilterTest {

    @Test
    public void alwaysExtractRootPathForZeroPathLength() {
        MetricsFilter metricsFilter = new MetricsFilter();
        String path = metricsFilter.extractPathFrom("/index.html", 0);
        assertThat(path, equalTo("/"));
    }

    @Test
    public void useAlwaysRootPathForLongPathLength() {
        MetricsFilter metricsFilter = new MetricsFilter();
        String path = metricsFilter.extractPathFrom("/index.html", 1);
        assertThat(path, equalTo("/"));
    }

    @Test
    public void pathDepthOneuseAlwaysRootPathForZeroPathLength() {
        MetricsFilter metricsFilter = new MetricsFilter();
        String path = metricsFilter.extractPathFrom("/test/index.html", 1);
        assertThat(path, equalTo("/test/"));
    }

    @Test
    public void cutsPathsLongerThanPathDepth() {
        MetricsFilter metricsFilter = new MetricsFilter();
        String path = metricsFilter.extractPathFrom("/test/tralala/index.html", 1);
        assertThat(path, equalTo("/test/"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void throwsExceptionForNegativePathDepth() {
        new MetricsFilter().extractPathFrom("/index.html", -1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void throwsExceptionForNullRequestPath() {
        new MetricsFilter().extractPathFrom(null, 1);
    }

}