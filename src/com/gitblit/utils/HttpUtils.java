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
	 * Returns the host URL based on the request.
	 * 
	 * @param request
	 * @return the host url
	 */
	public static String getHostURL(HttpServletRequest request) {
		StringBuilder sb = new StringBuilder();
		sb.append(request.getScheme());
		sb.append("://");
		sb.append(request.getServerName());
		if ((request.getScheme().equals("http") && request.getServerPort() != 80)
				|| (request.getScheme().equals("https") && request.getServerPort() != 443)) {
			sb.append(":" + request.getServerPort());
		}
		return sb.toString();
	}
}
