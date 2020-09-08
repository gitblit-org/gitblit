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

import static org.junit.Assert.*;

import java.util.Locale;

import org.junit.Test;

import com.gitblit.utils.ByteFormat;

public class ByteFormatTest {

	@Test
	public void testByteFormat() throws Exception {
		// sets locale for this test
		Locale defaultLocale = Locale.getDefault();

		try {
			Locale.setDefault(Locale.ENGLISH);
			ByteFormat format = new ByteFormat();
			assertEquals("10 b", format.format(10));
			assertEquals("10 KB", format.format(1024 * 10));
			assertEquals("1,000 KB", format.format(1024 * 1000));
			assertEquals("2.0 MB", format.format(2 * 1024 * 1000));
			assertEquals("1,000.0 MB", format.format(1024 * 1024 * 1000));
			assertEquals("2.0 GB", format.format(2 * 1024 * 1024 * 1000));
		} finally {
			Locale.setDefault(defaultLocale);
		}
	}
}
