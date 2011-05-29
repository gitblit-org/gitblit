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

import java.util.Arrays;

import junit.framework.TestCase;

import com.gitblit.utils.StringUtils;

public class StringUtilsTest extends TestCase {

	public void testIsEmpty() throws Exception {
		assertTrue(StringUtils.isEmpty(null));
		assertTrue(StringUtils.isEmpty(""));
		assertTrue(StringUtils.isEmpty("  "));
		assertFalse(StringUtils.isEmpty("A"));
	}

	public void testBreakLinesForHtml() throws Exception {
		String input = "this\nis\r\na\rtest\r\n\r\nof\n\nline\r\rbreaking";
		String output = "this<br/>is<br/>a<br/>test<br/><br/>of<br/><br/>line<br/><br/>breaking";
		assertTrue(StringUtils.breakLinesForHtml(input).equals(output));
	}

	public void testEscapeForHtml() throws Exception {
		String input = "& < > \" \t";
		String output_nochange = "&amp; &lt; &gt; &quot; \t";
		String output_change = "&amp;&nbsp;&lt;&nbsp;&gt;&nbsp;&quot;&nbsp; &nbsp; &nbsp;";
		assertTrue(StringUtils.escapeForHtml(input, false).equals(output_nochange));
		assertTrue(StringUtils.escapeForHtml(input, true).equals(output_change));
	}

	public void testFlattenStrings() throws Exception {
		String[] strings = { "A", "B", "C", "D" };
		assertTrue(StringUtils.flattenStrings(Arrays.asList(strings)).equals("A B C D"));
	}

	public void testTrim() throws Exception {
		String input = "123456789 123456789 123456789 123456789 123456789 123456789 123456789 ";
		String output = "123456789 123456789 123456789 123456789 123456789 1234567...";
		assertTrue(StringUtils.trimShortLog(input).equals(output));
		assertTrue(StringUtils.trimString(input, input.length()).equals(input));
	}

	public void testPadding() throws Exception {
		String input = "test";
		assertTrue(StringUtils.leftPad(input, 6 + input.length(), ' ').equals("      test"));
		assertTrue(StringUtils.rightPad(input, 6 + input.length(), ' ').equals("test      "));

		assertTrue(StringUtils.leftPad(input, input.length(), ' ').equals(input));
		assertTrue(StringUtils.rightPad(input, input.length(), ' ').equals(input));
	}

	public void testSHA1() throws Exception {
		assertTrue(StringUtils.getSHA1("blob 16\000what is up, doc?").equals(
				"bd9dbf5aae1a3862dd1526723246b20206e5fc37"));
	}

	public void testRootPath() throws Exception {
		String input = "/nested/path/to/repository";
		String output = "/nested/path/to";
		assertTrue(StringUtils.getRootPath(input).equals(output));
		assertTrue(StringUtils.getRootPath("repository").equals(""));
	}
}
