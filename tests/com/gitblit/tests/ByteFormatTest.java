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

import junit.framework.TestCase;

import com.gitblit.utils.ByteFormat;

public class ByteFormatTest extends TestCase {

	public void testByteFormat() throws Exception {
		ByteFormat format = new ByteFormat();
		assertTrue(format.format(10).equals("10 b"));
		assertTrue(format.format(1024 * 10).equals("10.0 KB"));
		assertTrue(format.format(1024 * 1000).equals("1,000.0 KB"));
		assertTrue(format.format(2 * 1024 * 1000).equals("2.0 MB"));
		assertTrue(format.format(1024 * 1024 * 1000).equals("1,000.0 MB"));
		assertTrue(format.format(2 * 1024 * 1024 * 1000).equals("2.0 GB"));
	}
}
