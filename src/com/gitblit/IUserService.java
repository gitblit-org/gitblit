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

import java.util.List;

import com.gitblit.models.UserModel;

public interface IUserService {

	boolean supportsCookies();

	char[] getCookie(UserModel model);

	UserModel authenticate(char[] cookie);

	UserModel authenticate(String username, char[] password);

	UserModel getUserModel(String username);

	boolean updateUserModel(UserModel model);

	boolean updateUserModel(String username, UserModel model);

	boolean deleteUserModel(UserModel model);

	boolean deleteUser(String username);

	List<String> getAllUsernames();

	List<String> getUsernamesForRepository(String role);

	boolean setUsernamesForRepository(String role, List<String> usernames);

	boolean renameRepositoryRole(String oldRole, String newRole);

	boolean deleteRepositoryRole(String role);

	String toString();
}
