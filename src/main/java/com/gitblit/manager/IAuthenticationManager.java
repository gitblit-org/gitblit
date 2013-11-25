/*
 * Copyright 2013 gitblit.com.
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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.gitblit.models.TeamModel;
import com.gitblit.models.UserModel;

public interface IAuthenticationManager extends IManager {

	/**
	 * Authenticate a user based on HTTP request parameters.
	 *
	 * Authentication by X509Certificate is tried first and then by cookie.
	 *
	 * @param httpRequest
	 * @return a user object or null
	 */
	UserModel authenticate(HttpServletRequest httpRequest);

	/**
	 * Authenticate a user based on HTTP request parameters.
	 *
	 * Authentication by X509Certificate, servlet container principal, cookie,
	 * and BASIC header.
	 *
	 * @param httpRequest
	 * @param requiresCertificate
	 * @return a user object or null
	 */
	UserModel authenticate(HttpServletRequest httpRequest, boolean requiresCertificate);

	/**
	 * Authenticate a user based on a username and password.
	 *
	 * @see IUserService.authenticate(String, char[])
	 * @param username
	 * @param password
	 * @return a user object or null
	 */
	UserModel authenticate(String username, char[] password);

	/**
	 * Sets a cookie for the specified user.
	 *
	 * @param response
	 * @param user
	 */
	void setCookie(HttpServletResponse response, UserModel user);

	/**
	 * Logout a user.
	 *
	 * @param user
	 */
	void logout(HttpServletResponse response, UserModel user);

	/**
	 * Does the user service support changes to credentials?
	 *
	 * @return true or false
	 * @since 1.0.0
	 */
	boolean supportsCredentialChanges(UserModel user);

	/**
	 * Returns true if the user's display name can be changed.
	 *
	 * @param user
	 * @return true if the user service supports display name changes
	 */
	boolean supportsDisplayNameChanges(UserModel user);

	/**
	 * Returns true if the user's email address can be changed.
	 *
	 * @param user
	 * @return true if the user service supports email address changes
	 */
	boolean supportsEmailAddressChanges(UserModel user);

	/**
	 * Returns true if the user's team memberships can be changed.
	 *
	 * @param user
	 * @return true if the user service supports team membership changes
	 */
	boolean supportsTeamMembershipChanges(UserModel user);

	/**
	 * Returns true if the team memberships can be changed.
	 *
	 * @param user
	 * @return true if the team memberships can be changed
	 */
	boolean supportsTeamMembershipChanges(TeamModel team);

}