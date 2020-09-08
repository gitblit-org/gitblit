/*
 * Copyright 2019 Tue Ton
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
package com.gitblit.repotests;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import com.gitblit.tests.GitBlitTestConfig;

public abstract class RepoUnitTest extends org.junit.Assert {

	@Rule
	public TestRule printTestClassAndMethod = new TestWatcher() {
		@Override
		protected void starting(Description description) {
			System.out.println("*****Starting test: class=" + description.getClassName() + ", method=" + description.getMethodName());
		}
	};

	@BeforeClass
	public static void setUpGitRepositories() throws Exception {
		GitBlitTestConfig.setUpGitRepositories();
	}

}
