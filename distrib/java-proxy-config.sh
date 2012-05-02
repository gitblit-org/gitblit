#!/bin/bash

# To set the proxy configuration, specify the following host name and port
#PROXY_HOST=
#PROXY_PORT=

# To exclude any hosts from proxy configuration such that they directly accessed by Gitblit without passing through the proxy server, append the host name to the following variable using "|" as the separator
NON_PROXY_HOSTS="localhost|127.0.0.*|*.local|192.168.*.*|10.193.*.*"

### The following should not need to be modified

JAVA_PROXY_CONFIG=""

if [ -n "${PROXY_HOST}" -a -n "${PROXY_PORT}" ]; then

    JAVA_PROXY_CONFIG=" -DproxySet=true -Dhttp.proxyHost=${PROXY_HOST} -Dhttp.proxyPort=${PROXY_PORT} -Dhttps.proxyHost=${PROXY_HOST} -Dhttps.proxyPort=${PROXY_PORT} -Dftp.proxyHost=${PROXY_HOST} -Dftp.proxyPort=${PROXY_PORT} "
fi

if [ -n "${PROXY_HOST}" -a -n "${PROXY_PORT}" -a -n "${NON_PROXY_HOSTS}" ]; then

    JAVA_PROXY_CONFIG="${JAVA_PROXY_CONFIG} -Dhttp.nonProxyHosts=\"${NON_PROXY_HOSTS}\" -Dftp.nonProxyHosts=\"${NON_PROXY_HOSTS}\" "
fi

export JAVA_PROXY_CONFIG

