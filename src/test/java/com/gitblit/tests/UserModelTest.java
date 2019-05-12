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
package com.gitblit.tests;

import static org.junit.Assert.*;

import org.junit.Test;

import com.gitblit.models.UserModel;

/**
 * @author Alfred Schmid
 *
 */
public class UserModelTest {

	@Test
	public void whenDisplayNameIsEmptyUsernameIsUsed() {
		String username = "test";
		UserModel userModel = new UserModel(username);
		userModel.displayName = "";
		assertEquals("When displayName is empty the username has to be returnd from getDisplayName().", username, userModel.getDisplayName());
	}

	@Test
	public void whenDisplayNameIsNullUsernameIsUsed() {
		String username = "test";
		UserModel userModel = new UserModel(username);
		userModel.displayName = null;
		assertEquals("When displayName is null the username has to be returnd from getDisplayName().", username, userModel.getDisplayName());
	}

	@Test
	public void whenDisplayNameIsNotEmptyDisplayNameIsUsed() {
		String displayName = "Test User";
		UserModel userModel = new UserModel("test");
		userModel.displayName = displayName;
		assertEquals("When displayName is not empty its value has to be returnd from getDisplayName().", displayName, userModel.getDisplayName());
	}

}
