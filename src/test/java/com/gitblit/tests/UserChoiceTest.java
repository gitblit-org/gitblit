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

import com.gitblit.models.UserChoice;

/**
 * Test behavior of UserChoice class.
 *
 * @author Alfred Schmid
 *
 */
public class UserChoiceTest {

	@Test(expected=IllegalArgumentException.class)
	public void creatingUserChoiceWithNullAsUserIdIsImpossible() {
		new UserChoice(null);
	}

	@Test(expected=IllegalArgumentException.class)
	public void creatingUserChoiceWithEmptyStringAsUserIdIsImpossible() {
		new UserChoice("");
	}

	@Test
	public void toStringPrintsPlainUserIdWhenDisplayNameIsNull() {
		String userId = "runnerr";
		UserChoice userChoice = new UserChoice(userId);
		assertEquals("", userId, userChoice.toString());
	}

	@Test
	public void toStringPrintsPlainUserIdWhenDisplayNameIsEmpty() {
		String userId = "runnerr";
		UserChoice userChoice = new UserChoice("", userId);
		assertEquals("", userId, userChoice.toString());
	}

	@Test
	public void toStringPrintsDisplaNameWithUserIdInBracketsWhenDisplayNameIsSet() {
		String userId = "runnerr";
		String displayName = "The Road Runner";
		UserChoice userChoice = new UserChoice(displayName, userId);
		assertEquals(
				"displayName + userId have to be concatenated to: displayName (userId)",
				displayName + " (" + userId + ")", userChoice.toString());
	}
}
