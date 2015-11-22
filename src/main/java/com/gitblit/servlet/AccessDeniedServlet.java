/*
 * Copyright 2015 Jean-Baptiste Mayer
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

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.inject.Singleton;

/**
 * Access-denied Servlet.
 * 
 * This servlet serves only 404 Not Found error replies.
 * 
 * This servlet is used to override the container's default behavior to serve
 * all contents of the application's WAR. We can selectively prevent access to
 * a specific path simply by mapping this servlet onto specific paths.
 * 
 * 
 * @author Jean-Baptiste Mayer
 *
 */
@Singleton
public class AccessDeniedServlet extends HttpServlet {
	/**
	 * 
	 */
	private static final long serialVersionUID = -3239463647917811122L;

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

	private void processRequest(HttpServletRequest request,
			HttpServletResponse response) {
		response.setStatus(HttpServletResponse.SC_NOT_FOUND);
	}
}
