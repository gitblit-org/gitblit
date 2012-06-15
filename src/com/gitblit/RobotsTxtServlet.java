/*
 * Copyright 2012 gitblit.com.
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
package com.gitblit;

import java.io.File;
import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.gitblit.utils.FileUtils;
import com.gitblit.utils.StringUtils;

/**
 * Handles requests for robots.txt
 * 
 * @author James Moger
 * 
 */
public class RobotsTxtServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	public RobotsTxtServlet() {
		super();
	}
	
	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, java.io.IOException {
		processRequest(request, response);
	}

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		processRequest(request, response);
	}

	protected void processRequest(javax.servlet.http.HttpServletRequest request,
			javax.servlet.http.HttpServletResponse response) throws javax.servlet.ServletException,
			java.io.IOException {
		String robotstxt = GitBlit.getString(Keys.web.robots.txt, null);		
		String content = "";
		if (!StringUtils.isEmpty(robotstxt)) {
			File robotsfile = new File(robotstxt);
			if (robotsfile.exists()) {
				content = FileUtils.readContent(robotsfile, "\n");
			}
		}
		response.getWriter().append(content);
	}
}
