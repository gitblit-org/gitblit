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

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Map;

import com.gitblit.Constants;
import com.gitblit.Constants.RpcRequest;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.google.gson.reflect.TypeToken;

/**
 * Utility methods for rpc calls.
 * 
 * @author James Moger
 * 
 */
public class RpcUtils {

	public static final Type REPOSITORIES_TYPE = new TypeToken<Map<String, RepositoryModel>>() {
	}.getType();

	public static final Type USERS_TYPE = new TypeToken<Collection<UserModel>>() {
	}.getType();

	/**
	 * 
	 * @param remoteURL
	 *            the url of the remote gitblit instance
	 * @param req
	 *            the rpc request type
	 * @return
	 */
	public static String asLink(String remoteURL, RpcRequest req) {
		if (remoteURL.length() > 0 && remoteURL.charAt(remoteURL.length() - 1) == '/') {
			remoteURL = remoteURL.substring(0, remoteURL.length() - 1);
		}
		if (req == null) {
			req = RpcRequest.LIST_REPOSITORIES;
		}
		return remoteURL + Constants.RPC_PATH + "?req=" + req.name().toLowerCase();
	}
	
	/**
	 * Retrieves a map of the repositories at the remote gitblit instance keyed
	 * by the repository clone url.
	 * 
	 * @param serverUrl
	 * @return a map of cloneable repositories
	 * @throws Exception
	 */
	public static Map<String, RepositoryModel> getRepositories(String serverUrl) throws Exception {
		String url = asLink(serverUrl, RpcRequest.LIST_REPOSITORIES);
		Map<String, RepositoryModel> models = JsonUtils.retrieveJson(url, REPOSITORIES_TYPE);
		return models;
	}

	/**
	 * Tries to pull the gitblit user accounts from the remote gitblit instance.
	 * 
	 * @param serverUrl
	 * @return a collection of UserModel objects
	 * @throws Exception
	 */
	public static Collection<UserModel> getUsers(String serverUrl) throws Exception {
		String url = asLink(serverUrl, RpcRequest.LIST_USERS);
		Collection<UserModel> models = JsonUtils.retrieveJson(url, USERS_TYPE);
		return models;
	}
}
