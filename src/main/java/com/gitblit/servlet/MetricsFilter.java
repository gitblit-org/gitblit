package com.gitblit.servlet;

import io.prometheus.client.Counter;
import io.prometheus.client.Histogram;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 */
public class MetricsFilter implements Filter {
    public static final String PARAM_PATH_MAX_DEPTH = "max-path-depth";
    public static final String PARAM_DURATION_HIST_BUCKET_CONFIG = "request-duration-histogram-buckets";

    private Histogram httpRequestDuration = null;
    private Counter requests = null;

    // Package-level for testing purposes.
    int pathDepth = 1;
    private double[] buckets = null;

    public MetricsFilter() {
    }

    public MetricsFilter(
            Integer maxPathDepth,
            double[] buckets
    ) throws ServletException {
        this.buckets = buckets;
        if (maxPathDepth != null) {
            this.pathDepth = maxPathDepth;
        }
        this.init(null);
    }

    private boolean isEmpty(String s) {
        return s == null || s.length() == 0;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

        Histogram.Builder httpRequestDurationBuilder = Histogram.build()
                .name("http_request_duration_seconds")
                .labelNames("path", "method")
                .help("The time taken fulfilling servlet requests");

        Counter.Builder requestsBuilder = Counter.build()
                .name("http_requests_total")
                .help("Total requests.")
                .labelNames("path", "method", "status");

        if (filterConfig == null && isEmpty("http_request_duration")) {
            throw new ServletException("No configuration object provided, and no metricName passed via constructor");
        }

        if (filterConfig != null) {

            // Allow overriding of the path "depth" to track
            if (!isEmpty(filterConfig.getInitParameter(PARAM_PATH_MAX_DEPTH))) {
                pathDepth = Integer.valueOf(filterConfig.getInitParameter(PARAM_PATH_MAX_DEPTH));
            }

            // Allow users to override the default bucket configuration
            if (!isEmpty(filterConfig.getInitParameter(PARAM_DURATION_HIST_BUCKET_CONFIG))) {
                String[] bucketParams = filterConfig.getInitParameter(PARAM_DURATION_HIST_BUCKET_CONFIG).split(",");
                buckets = new double[bucketParams.length];

                for (int i = 0; i < bucketParams.length; i++) {
                    buckets[i] = Double.parseDouble(bucketParams[i]);
                }
            }
        }

        requests = requestsBuilder.register();

        if (buckets != null) {
            httpRequestDurationBuilder = httpRequestDurationBuilder.buckets(buckets);
        }

        httpRequestDuration = httpRequestDurationBuilder.register();
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        if (!(servletRequest instanceof HttpServletRequest)) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String path = request.getRequestURI();
        String normalizedPath = extractPathFrom(path, pathDepth);

        Histogram.Timer timer = httpRequestDuration
                .labels(normalizedPath, request.getMethod())
                .startTimer();
        try {
            filterChain.doFilter(servletRequest, servletResponse);
            requests.labels(normalizedPath, request.getMethod().toUpperCase(), String.valueOf(response.getStatus())).inc();
        } finally {
            timer.observeDuration();
        }
    }

    public String extractPathFrom(String requestUri, int maxPathDepth) {
        if (maxPathDepth < 0 || requestUri == null) {
            throw new IllegalArgumentException("Path depth has to >= 0");
        }

        int count = 0;
        int pathPosition = -1;
        do {
            int lastPathPosition = pathPosition;
            pathPosition = requestUri.indexOf("/", pathPosition + 1);
            if (count > maxPathDepth || pathPosition < 0) {
                return requestUri.substring(0, lastPathPosition + 1);
            }
            count++;
        } while (count <= maxPathDepth);

        return requestUri.substring(0, pathPosition + 1);
    }

    @Override
    public void destroy() {
    }

}

