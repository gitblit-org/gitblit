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
package com.gitblit.models;

import java.io.Serializable;
import java.util.Map;

import com.gitblit.Constants.FederationToken;

/**
 * Represents a group of repositories.
 */
public class FederationSet implements Serializable {

	private static final long serialVersionUID = 1L;

	public String name;

	public String token;

	public FederationToken tokenType;

	public Map<String, RepositoryModel> repositories;

	/**
	 * The constructor for a federation set.
	 * 
	 * @param name
	 *            the name of this federation set
	 * @param tokenType
	 *            the type of token of this federation set
	 * @param token
	 *            the federation token
	 */
	public FederationSet(String name, FederationToken tokenType, String token) {
		this.name = name;
		this.tokenType = tokenType;
		this.token = token;
	}

	@Override
	public String toString() {
		return "Federation Set (" + name + ")";
	}
}
