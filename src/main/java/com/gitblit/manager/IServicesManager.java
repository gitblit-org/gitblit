/*
 * Copyright 2014 gitblit.com.
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
package com.gitblit.manager;

import java.util.List;

import javax.servlet.http.HttpServletRequest;

import com.gitblit.Constants.Transport;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.RepositoryUrl;
import com.gitblit.models.UserModel;

public interface IServicesManager extends IManager {

	/**
	 * Determine if this Gitblit instance is actively serving git repositories
	 * or if it is merely a repository viewer.
	 *
	 * @return true if Gitblit is serving repositories
 	 * @since 1.7.0
	 */
	boolean isServingRepositories();

	/**
	 * Determine if this Gitblit instance is actively serving git repositories
	 * over HTTP.
	 *
	 * @return true if Gitblit is serving repositories over HTTP
 	 * @since 1.7.0
	 */
	boolean isServingHTTP();

	/**
	 * Determine if this Gitblit instance is actively serving git repositories
	 * over HTTP.
	 *
	 * @return true if Gitblit is serving repositories over HTTPS
 	 * @since 1.7.0
	 */
	boolean isServingHTTPS();

	/**
	 * Determine if this Gitblit instance is actively serving git repositories
	 * over the GIT Daemon protocol.
	 *
	 * @return true if Gitblit is serving repositories over the GIT Daemon protocol
 	 * @since 1.7.0
	 */
	boolean isServingGIT();

	/**
	 * Determine if this Gitblit instance is actively serving git repositories
	 * over the SSH protocol.
	 *
	 * @return true if Gitblit is serving repositories over the SSH protocol
 	 * @since 1.7.0
	 */
	boolean isServingSSH();

	/**
	 * Returns a list of repository URLs and the user access permission.
	 *
	 * @param request
	 * @param user
	 * @param repository
	 * @return a list of repository urls
	 * @since 1.7.0
	 */
	List<RepositoryUrl> getRepositoryUrls(HttpServletRequest request, UserModel user, RepositoryModel repository);

	/**
	 * Returns true if the transport may be used for pushing.
	 *
	 * @param byTransport
	 * @return true if the transport can be used for pushes.
	 * @since 1.7.0
	 */
	boolean acceptsPush(Transport byTransport);

}