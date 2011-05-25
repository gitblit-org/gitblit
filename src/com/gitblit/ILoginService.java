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

import com.gitblit.wicket.models.UserModel;

public interface ILoginService {

	UserModel authenticate(String username, char[] password);

	UserModel getUserModel(String username);
	
	boolean updateUserModel(UserModel model);
	
	boolean updateUserModel(String username, UserModel model);
	
	boolean deleteUserModel(UserModel model);
	
	boolean deleteUser(String username);
	
	List<String> getAllUsernames();
	
	List<String> getUsernamesForRole(String role);
	
	boolean setUsernamesForRole(String role, List<String> usernames);
	
	boolean renameRole(String oldRole, String newRole);
	
	boolean deleteRole(String role);
}
