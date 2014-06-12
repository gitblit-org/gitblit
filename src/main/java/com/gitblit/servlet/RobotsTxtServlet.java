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
package com.gitblit.servlet;

import java.io.File;
import java.io.IOException;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.gitblit.Keys;
import com.gitblit.manager.IRuntimeManager;
import com.gitblit.utils.FileUtils;

/**
 * Handles requests for robots.txt
 *
 * @author James Moger
 *
 */
@Singleton
public class RobotsTxtServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	private IRuntimeManager runtimeManager;

	@Inject
	public RobotsTxtServlet(IRuntimeManager runtimeManager) {
		this.runtimeManager = runtimeManager;
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
		File file = runtimeManager.getFileOrFolder(Keys.web.robots.txt, null);
		String content = "";
		if (file.exists()) {
			content = FileUtils.readContent(file, "\n");
		}
		response.getWriter().append(content);
	}
}
