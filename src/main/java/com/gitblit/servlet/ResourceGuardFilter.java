/*
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
package com.gitblit.servlet;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.wicket.util.string.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Prevent accidental access to 'resources' such as GitBlit java classes
 * 
 * In the GO setup the JAR containing the application and the WAR injected
 * into Jetty are the same file. However Jetty expects to serve the entire WAR
 * contents, except the WEB-INF folder. Thus, all java binary classes in the
 * JAR are served as is they were legitimate resources.
 * 
 * This filter allows us to explicitly deny access to some folders
 * 
 * @author Jean-Baptiste Mayer
 *
 */
public class ResourceGuardFilter implements Filter {

	private Set<String> deniedPaths = new HashSet<String>();
	private static final Logger logger = LoggerFactory.getLogger(ResourceGuardFilter.class);

	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
		String paths = filterConfig.getInitParameter("deniedPaths");
		if (!Strings.isEmpty(paths))
		{
			String[] parts = paths.split(",");
			for (String path : parts)
			{
				if (path.startsWith("/"))
				{
					path = path.substring(1);
				}
				deniedPaths.add(path);
			}
		}
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response,
			FilterChain chain) throws IOException, ServletException {
		HttpServletRequest httpRequest = (HttpServletRequest)request;
		HttpServletResponse httpResponse = (HttpServletResponse)response;

		String relativePath = httpRequest.getServletPath();
		if(relativePath.startsWith("/"))
		{
			relativePath = relativePath.substring(1);
		}
		
		// check against denied paths, respond with a access denied status if a match is found
		if (deniedPaths.size() > 0 && relativePath.length() > 0)
		{
			for (String path : deniedPaths)
			{
				if (relativePath.startsWith(path))
				{
					logger.debug("Rejecting request {}", httpRequest.getRequestURL());
					httpResponse.setStatus(HttpServletResponse.SC_NOT_FOUND);
					return;
				}
			}
		}
		chain.doFilter(request, response);
		return;
	}

	@Override
	public void destroy() {

	}
	
}
