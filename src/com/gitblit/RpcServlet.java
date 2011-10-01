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

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import com.gitblit.Constants.RpcRequest;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.HttpUtils;

/**
 * Handles remote procedure calls.
 * 
 * @author James Moger
 * 
 */
public class RpcServlet extends JsonServlet {

	private static final long serialVersionUID = 1L;

	public RpcServlet() {
		super();
	}

	/**
	 * Processes an rpc request.
	 * 
	 * @param request
	 * @param response
	 * @throws javax.servlet.ServletException
	 * @throws java.io.IOException
	 */
	@Override
	protected void processRequest(javax.servlet.http.HttpServletRequest request,
			javax.servlet.http.HttpServletResponse response) throws javax.servlet.ServletException,
			java.io.IOException {
		RpcRequest reqType = RpcRequest.fromName(request.getParameter("req"));
		logger.info(MessageFormat.format("Rpc {0} request from {1}", reqType,
				request.getRemoteAddr()));

		if (!GitBlit.getBoolean(Keys.web.enableRpcServlet, false)) {
			logger.warn(Keys.web.enableRpcServlet + " must be set TRUE for rpc requests.");
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
			return;
		}

		// TODO user authentication and authorization
		UserModel user = null;

		Object result = null;
		if (RpcRequest.LIST_REPOSITORIES.equals(reqType)) {
			// list repositories

			// Determine the Gitblit clone url
			String gitblitUrl = HttpUtils.getGitblitURL(request);
			StringBuilder sb = new StringBuilder();
			sb.append(gitblitUrl);
			sb.append(Constants.GIT_PATH);
			sb.append("{0}");
			String cloneUrl = sb.toString();

			List<RepositoryModel> list = GitBlit.self().getRepositoryModels(user);
			Map<String, RepositoryModel> repositories = new HashMap<String, RepositoryModel>();
			for (RepositoryModel model : list) {
				String url = MessageFormat.format(cloneUrl, model.name);
				repositories.put(url, model);
			}
			result = repositories;
		} else if (RpcRequest.LIST_USERS.equals(reqType)) {
			// list users
			if (user == null || !user.canAdmin) {
				response.sendError(HttpServletResponse.SC_FORBIDDEN);
				return;
			}
			// user is authorized to retrieve all accounts
			List<String> names = GitBlit.self().getAllUsernames();
			List<UserModel> users = new ArrayList<UserModel>();
			for (String name : names) {
				users.add(GitBlit.self().getUserModel(name));
			}
			result = users;
		}

		// send the result of the request
		serialize(response, result);
	}
}
