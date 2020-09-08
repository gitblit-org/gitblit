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

import org.junit.Test;

import com.gitblit.utils.Base64;

public class Base64Test {

	@Test
	public void testBase64() {
		String source = "this is a test";
		String base64 = Base64.encodeBytes(source.getBytes());
		assertEquals("dGhpcyBpcyBhIHRlc3Q=", base64);
		String decoded = new String(Base64.decode(base64));
		assertEquals(source, decoded);
	}
}