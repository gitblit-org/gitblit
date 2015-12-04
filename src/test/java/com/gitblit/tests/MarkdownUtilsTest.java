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

import org.junit.Test;

import com.gitblit.utils.MarkdownUtils;

public class MarkdownUtilsTest extends GitblitUnitTest {

	@Test
	public void testMarkdown() throws Exception {
		assertEquals("<h1>H1</h1>", MarkdownUtils.transformMarkdown("# H1"));
		assertEquals("<h2>H2</h2>", MarkdownUtils.transformMarkdown("## H2"));
		assertEquals("<p><strong>THIS</strong> is a test</p>",
				MarkdownUtils.transformMarkdown("**THIS** is a test"));
		assertEquals("<p>** THIS ** is a test</p>",
				MarkdownUtils.transformMarkdown("** THIS ** is a test"));
		assertEquals("<p>**THIS ** is a test</p>",
				MarkdownUtils.transformMarkdown("**THIS ** is a test"));
		assertEquals("<p>** THIS** is a test</p>",
				MarkdownUtils.transformMarkdown("** THIS** is a test"));
		assertEquals("<p>This is also a test</p>",
				MarkdownUtils.transformMarkdown("This is also\na test"));

		assertEquals("<table><tr><td>test</td></tr></table>",
				MarkdownUtils.transformMarkdown("<table><tr><td>test</td></tr></table>"));
		assertEquals("<table><tr><td>&lt;test&gt;</td></tr></table>",
				MarkdownUtils.transformMarkdown("<table><tr><td>&lt;test&gt;</td></tr></table>"));
	}
}