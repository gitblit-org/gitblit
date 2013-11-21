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
package com.gitblit.servlet;

import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.text.MessageFormat;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.Constants;
import com.gitblit.utils.JsonUtils;
import com.gitblit.utils.StringUtils;

/**
 * Servlet class for interpreting json requests.
 *
 * @author James Moger
 *
 */
public abstract class JsonServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	protected final int forbiddenCode = HttpServletResponse.SC_FORBIDDEN;

	protected final int notAllowedCode = HttpServletResponse.SC_METHOD_NOT_ALLOWED;

	protected final int failureCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;

	protected final Logger logger;

	public JsonServlet() {
		super();
		logger = LoggerFactory.getLogger(getClass());
	}

	/**
	 * Processes an gson request.
	 *
	 * @param request
	 * @param response
	 * @throws javax.servlet.ServletException
	 * @throws java.io.IOException
	 */
	protected abstract void processRequest(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException;

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

	protected <X> X deserialize(HttpServletRequest request, HttpServletResponse response,
			Class<X> clazz) throws IOException {
		String json = readJson(request, response);
		if (StringUtils.isEmpty(json)) {
			return null;
		}

		X object = JsonUtils.fromJsonString(json.toString(), clazz);
		return object;
	}

	protected <X> X deserialize(HttpServletRequest request, HttpServletResponse response, Type type)
			throws IOException {
		String json = readJson(request, response);
		if (StringUtils.isEmpty(json)) {
			return null;
		}

		X object = JsonUtils.fromJsonString(json.toString(), type);
		return object;
	}

	private String readJson(HttpServletRequest request, HttpServletResponse response)
			throws IOException {
		BufferedReader reader = request.getReader();
		StringBuilder json = new StringBuilder();
		String line = null;
		while ((line = reader.readLine()) != null) {
			json.append(line);
		}
		reader.close();

		if (json.length() == 0) {
			logger.error(MessageFormat.format("Failed to receive json data from {0}",
					request.getRemoteAddr()));
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			return null;
		}
		return json.toString();
	}

	protected void serialize(HttpServletResponse response, Object o) throws IOException {
		if (o != null) {
			// Send JSON response
			String json = JsonUtils.toJsonString(o);
			response.setCharacterEncoding(Constants.ENCODING);
			response.setContentType("application/json");
			response.getWriter().append(json);
		}
	}
}
