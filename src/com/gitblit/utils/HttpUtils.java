/*
 * Copyright 2011 gitblit.com.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gitblit.utils;

import javax.servlet.http.HttpServletRequest;

/**
 * Collection of utility methods for http requests.
 * 
 * @author James Moger
 * 
 */
public class HttpUtils {

	/**
	 * Returns the Gitblit URL based on the request.
	 * 
	 * @param request
	 * @return the host url
	 */
	public static String getGitblitURL(HttpServletRequest request) {
		// default to the request scheme and port
		String scheme = request.getScheme();
		int port = request.getServerPort();

		// try to use reverse-proxy server's port
        String forwardedPort = request.getHeader("X-Forwarded-Port");
        if (StringUtils.isEmpty(forwardedPort)) {
        	forwardedPort = request.getHeader("X_Forwarded_Port");
        }
        if (!StringUtils.isEmpty(forwardedPort)) {
        	// reverse-proxy server has supplied the original port
        	try {
        		port = Integer.parseInt(forwardedPort);
        	} catch (Throwable t) {
        	}
        }
        
		// try to use reverse-proxy server's scheme
        String forwardedScheme = request.getHeader("X-Forwarded-Proto");
        if (StringUtils.isEmpty(forwardedScheme)) {
        	forwardedScheme = request.getHeader("X_Forwarded_Proto");
        }
        if (!StringUtils.isEmpty(forwardedScheme)) {
        	// reverse-proxy server has supplied the original scheme
        	scheme = forwardedScheme;
        	
        	if ("https".equals(scheme) && port == 80) {
        		// proxy server is https, inside server is 80
        		// this is likely because the proxy server has not supplied
        		// x-forwarded-port. since 80 is almost definitely wrong,
        		// make an educated guess that 443 is correct.
        		port = 443;
        	}
        }
        
		StringBuilder sb = new StringBuilder();
		sb.append(scheme);
		sb.append("://");
		sb.append(request.getServerName());
		if (("http".equals(scheme) && port != 80)
				|| ("https".equals(scheme) && port != 443)) {
			sb.append(":" + port);
		}
		sb.append(request.getContextPath());
		return sb.toString();
	}
}
