package com.gitblit.service;

class PrometheusMetrics {

    /** Number of garbage collects  */
    static final String GIT_GARBAGE_COLLECTS_TOTAL = "gitblit_garbage_collects_total";

    /** Ldap Sync Latency in second */
    static final String LDAP_SYNC_LATENCY_SECONDS = "gitblit_ldap_sync_latency_seconds";
}
