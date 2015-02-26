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
package com.gitblit.tests;

import com.gitblit.utils.PathUtils;
import org.junit.Test;

import java.util.Arrays;

public class PathUtilsTest extends GitblitUnitTest {

	private static final String[][][] testData = {

			{
					// Folder contents
					{".gitignore","src/main/java/a.java", "src/main/java/b.java", "docs/c.md"},
					// Expected after compressing
					{".gitignore", "src/main/java/", "docs/"}
			},

			{
					{".gitignore","src/main/java/a.java", "src/main/b.java", "docs/c.md"},
					{".gitignore", "src/main/", "docs/"}
			},

			{
					{".gitignore","src/x.java","src/main/java/a.java", "src/main/java/b.java", "docs/c.md"},
					{".gitignore", "src/", "docs/"}
			},
	};




	@Test
	public void testCompressPaths() throws Exception {

		for (String[][] test : testData ) {
			assertArrayEquals(test[1], PathUtils.compressPaths(Arrays.asList(test[0])).toArray(new String[]{}));
		}

	}

	@Test
	public void testGetLastPathComponent() {
		assertEquals(PathUtils.getLastPathComponent("/a/b/c/d/e.out"), "e.out");
		assertEquals(PathUtils.getLastPathComponent("e.out"), "e.out");
		assertEquals(PathUtils.getLastPathComponent("/a/b/c/d/"), "d");
		assertEquals(PathUtils.getLastPathComponent("/a/b/c/d"), "d");
		assertEquals(PathUtils.getLastPathComponent("/"), "/");
	}

}