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
package com.gitblit;

import java.io.BufferedReader;
import java.io.IOException;
import java.text.MessageFormat;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Servlet class for interpreting json requests.
 * 
 * @author James Moger
 * 
 */
public abstract class JsonServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

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

		Gson gson = new Gson();
		X object = gson.fromJson(json.toString(), clazz);
		return object;
	}

	protected void serialize(HttpServletResponse response, Object o) throws IOException {
		if (o != null) {
			// Send JSON response
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			String json = gson.toJson(o);
			response.getWriter().append(json);
		}
	}
}
