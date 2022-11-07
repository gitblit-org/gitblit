/*
 * Copyright (c) 2013 by syntevo GmbH. All Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  o Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  o Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  o Neither the name of syntevo GmbH nor the names of
 *    its contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.syntevo.bugtraq;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringTokenizer;

import junit.framework.TestCase;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BugtraqFormatterTest extends TestCase {

	// Accessing ==============================================================

	public void testSimpleWithExtendedLink() throws BugtraqException {
		final BugtraqFormatter formatter = createFormatter(createEntry("https://jira.atlassian.com/browse/JRA-%BUGID%", null, "JRA-\\d+", "\\d+", null, null));
		doTest(formatter, "JRA-7399: Email subject formatting", l("JRA-7399", "https://jira.atlassian.com/browse/JRA-7399"), t(": Email subject formatting"));
		doTest(formatter, " JRA-7399, JRA-7398: Email subject formatting", t(" "), l("JRA-7399", "https://jira.atlassian.com/browse/JRA-7399"), t(", "), l("JRA-7398", "https://jira.atlassian.com/browse/JRA-7398"), t(": Email subject formatting"));
		doTest(formatter, "Fixed JRA-7399", t("Fixed "), l("JRA-7399", "https://jira.atlassian.com/browse/JRA-7399"));
	}

	public void testLinkText() throws BugtraqException {
		final BugtraqFormatter formatter = createFormatter(createEntry("https://jira.atlassian.com/browse/JRA-%BUGID%", null, "JRA-\\d+", "\\d+", "JIRA-%BUGID%", null));
		doTest(formatter, " JRA-7399, JRA is text, JRA-7398: Email subject formatting", t(" "), l("JIRA-7399", "https://jira.atlassian.com/browse/JRA-7399"), t(", JRA is text, "), l("JIRA-7398", "https://jira.atlassian.com/browse/JRA-7398"), t(": Email subject formatting"));
	}

	public void testTwoNonIntersectingConfigurations() throws BugtraqException {
		final BugtraqFormatter formatter = createFormatter(createEntry("https://jira.atlassian.com/browse/%BUGID%", null, null, "JRA-\\d+", null, null),
		                                                   createEntry("https://issues.apache.org/jira/browse/%BUGID%", null, null, "VELOCITY-\\d+", null, null));
		doTest(formatter, "JRA-7399, VELOCITY-847: fix", l("JRA-7399", "https://jira.atlassian.com/browse/JRA-7399"), t(", "), l("VELOCITY-847", "https://issues.apache.org/jira/browse/VELOCITY-847"), t(": fix"));
		doTest(formatter, " JRA-7399: fix/VELOCITY-847", t(" "), l("JRA-7399", "https://jira.atlassian.com/browse/JRA-7399"), t(": fix/"), l("VELOCITY-847", "https://issues.apache.org/jira/browse/VELOCITY-847"));
		doTest(formatter, "JRA-7399VELOCITY-847", l("JRA-7399", "https://jira.atlassian.com/browse/JRA-7399"), l("VELOCITY-847", "https://issues.apache.org/jira/browse/VELOCITY-847"));
	}

	public void testTwoIntersectingConfigurations() throws BugtraqException {
		final BugtraqFormatter formatter = createFormatter(createEntry("https://host1/%BUGID%", null, null, "A[AB]", null, null),
		                                                   createEntry("https://host2/%BUGID%", null, null, "BA[A]?", null, null));
		doTest(formatter, "AA: fix", l("AA", "https://host1/AA"), t(": fix"));
		doTest(formatter, "AB: fix", l("AB", "https://host1/AB"), t(": fix"));
		doTest(formatter, "BA: fix", l("BA", "https://host2/BA"), t(": fix"));
		doTest(formatter, "BAA: fix", l("BAA", "https://host2/BAA"), t(": fix"));
		doTest(formatter, "BAAA: fix", l("BAA", "https://host2/BAA"), t("A: fix"));
		doTest(formatter, "BAAAA: fix", l("BAA", "https://host2/BAA"), l("AA", "https://host1/AA"), t(": fix"));
		doTest(formatter, "BAAAAA: fix", l("BAA", "https://host2/BAA"), l("AA", "https://host1/AA"), t("A: fix"));
		doTest(formatter, "BAAABA: fix", l("BAA", "https://host2/BAA"), l("AB", "https://host1/AB"), t("A: fix"));
		doTest(formatter, "BAAABAA: fix", l("BAA", "https://host2/BAA"), l("AB", "https://host1/AB"), l("AA", "https://host1/AA"), t(": fix"));
		doTest(formatter, "BAAB: fix", l("BAA", "https://host2/BAA"), t("B: fix"));
		doTest(formatter, "BAAAB: fix", l("BAA", "https://host2/BAA"), l("AB", "https://host1/AB"), t(": fix"));
		doTest(formatter, "BAABBA: fix", l("BAA", "https://host2/BAA"), t("B"), l("BA", "https://host2/BA"), t(": fix"));
	}

	public void testMultipleProjects() throws BugtraqException {
		final BugtraqFormatter formatter = createFormatter(createEntry("https://jira.atlassian.com/browse/%PROJECT%-%BUGID%", null, "%PROJECT%-\\d+", "\\d+", null, "JRA,JRB,JRC"));
		doTest(formatter, "JRA-7399: Email subject formatting", l("JRA-7399", "https://jira.atlassian.com/browse/JRA-7399"), t(": Email subject formatting"));
		doTest(formatter, " JRA-7399, JRB-7398: Email subject formatting", t(" "), l("JRA-7399", "https://jira.atlassian.com/browse/JRA-7399"), t(", "), l("JRB-7398", "https://jira.atlassian.com/browse/JRB-7398"), t(": Email subject formatting"));
		doTest(formatter, "Fixed JRC-7399", t("Fixed "), l("JRC-7399", "https://jira.atlassian.com/browse/JRC-7399"));
	}

	// Utils ==================================================================

	private BugtraqFormatter createFormatter(BugtraqConfigEntry ... entries) {
		return new BugtraqFormatter(new BugtraqConfig(Arrays.asList(entries)));
	}
	
	private BugtraqConfigEntry createEntry(String url, @Nullable String filterRegex, @Nullable String linkRegex, @NotNull String idRegex, @Nullable String linkText, @Nullable String projectsList) throws BugtraqException {
		final List<String> projects;
		if (projectsList != null) {
			projects = new ArrayList<>();

			final StringTokenizer tokenizer = new StringTokenizer(projectsList, ",", false);
			while (tokenizer.hasMoreTokens()) {
				projects.add(tokenizer.nextToken());
			}
		}
		else {
			projects = null;
		}

		return new BugtraqConfigEntry(url, idRegex, linkRegex, filterRegex, linkText, projects);
	}

	private Text t(String text) {
		return new Text(text);
	}

	private Link l(String text, String url) {
		return new Link(text, url);
	}

	private void doTest(BugtraqFormatter formatter, String message, Atom ... expectedAtoms) {
		final List<Atom> actualAtoms = new ArrayList<Atom>();
		final StringBuilder sb = new StringBuilder();
		formatter.formatLogMessage(message, new BugtraqFormatter.OutputHandler() {
			@Override
			public void appendText(@NotNull String text) {
				actualAtoms.add(t(text));
				sb.append(text);
			}

			@Override
			public void appendLink(@NotNull String name, @NotNull String target) {
				actualAtoms.add(l(name, target));
				sb.append(name);
			}
		});

		assertEquals(Arrays.asList(expectedAtoms), actualAtoms);
	}

	// Inner Classes ==========================================================

	private static interface Atom {
	}

	private static class Text implements Atom {
		private final String text;

		private Text(String text) {
			this.text = text;
		}

		@Override
		public String toString() {
			return text;
		}

		@Override
		public int hashCode() {
			return text.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == null || obj.getClass() != getClass()) {
				return false;
			}

			return text.equals(((Text)obj).text);
		}
	}

	private static class Link implements Atom {
		private final String text;
		private final String url;

		private Link(String text, String url) {
			this.text = text;
			this.url = url;
		}

		@Override
		public String toString() {
			return "(" + text + "," + url + ")";
		}

		@Override
		public int hashCode() {
			return text.hashCode() ^ url.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == null || obj.getClass() != getClass()) {
				return false;
			}

			return text.equals(((Link)obj).text)
					&& url.equals(((Link)obj).url);
		}
	}
}