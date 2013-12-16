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

import junit.framework.*;

import java.util.*;

import org.jetbrains.annotations.*;

public class BugtraqParserTest extends TestCase {

	// Accessing ==============================================================

	public void testSimple1() throws BugtraqException {
		final BugtraqParser parser = createParser(null, null, "\\d+");
		doTest("", parser);
		doTest("1", parser, id(0, 0, "1"));
		doTest("1 2 3", parser, id(0, 0, "1"), id(2, 2, "2"), id(4, 4, "3"));
	}

	public void testSimple2() throws BugtraqException {
		final BugtraqParser parser = createParser(null, null, "(\\d+)");
		doTest("1", parser, id(0, 0, "1"));
		doTest("1 2 3", parser, id(0, 0, "1"), id(2, 2, "2"), id(4, 4, "3"));
	}

	public void testSimple3() throws BugtraqException {
		final BugtraqParser parser = createParser(null, null, "(SG-\\d+)");
		doTest("SG-1", parser, id(0, 3, "SG-1"));
		doTest("SG-1 SG-2 SG-3", parser, id(0, 3, "SG-1"), id(5, 8, "SG-2"), id(10, 13, "SG-3"));
	}

	public void testSimple4() throws BugtraqException {
		final BugtraqParser parser = createParser(null, null, "SG-(\\d+)");
		doTest("SG-1", parser, id(3, 3, "1"));
		doTest("SG-1 SG-2 SG-3", parser, id(3, 3, "1"), id(8, 8, "2"), id(13, 13, "3"));
	}

	public void testFilter1() throws BugtraqException {
		final BugtraqParser parser = createParser("(SG-\\d+)", null, "\\d+");
		doTest("SG-1", parser, id(3, 3, "1"));
		doTest("SG-1 SG-2 SG-3", parser, id(3, 3, "1"), id(8, 8, "2"), id(13, 13, "3"));
	}

	public void testFilter2() throws BugtraqException {
		final BugtraqParser parser = createParser("xSG-\\d+x", null, "\\d+");
		doTest("SG-1 xSG-2x SG-3", parser, id(9, 9, "2"));
	}

	public void testFilter3() throws BugtraqException {
		final BugtraqParser parser = createParser("[Ii]ssues?:?((\\s*(,|and)?\\s*#\\d+)+)", null, "\\d+");
		doTest("Issues #3, #4 and #5: Git Bugtraq Configuration options (see #12)", parser, id(8, 8, "3"), id(12, 12, "4"), id(19, 19, "5"));
	}

	public void testLink() throws BugtraqException {
		final BugtraqParser parser = createParser(null, "(SG-\\d+)", "\\d+");
		doTest("SG-1", parser, id(0, 3, "1"));
		doTest("SG-1 SG-2 SG-3", parser, id(0, 3, "1"), id(5, 8, "2"), id(10, 13, "3"));
	}

	public void testLinkAndFilter() throws BugtraqException {
		final BugtraqParser parser = createParser("[ab]\\d[cd]", "a\\dc|b\\dd", "\\d");
		doTest("a1c a2d b3c b4d", parser, id(0, 2, "1"), id(12, 14, "4"));
	}

	public void testFogBugz() throws BugtraqException {
		final BugtraqParser parser = createParser("(?:Bug[zs]?\\s*IDs?\\s*|Cases?)[#:; ]+((\\d+[ ,:;#]*)+)", "[#]?\\d+", "\\d+");
		doTest("Bug IDs: 3, #4, 5", parser, id(9, 9, "3"), id(12, 13, "4"), id(16, 16, "5"));
	}

	public void testFogBugzInvalid() throws BugtraqException {
		final BugtraqParser parser = createParser("Bug[zs]?\\s*IDs?\\s*|Cases?[#:; ]+((\\d+[ ,:;#]*)+)", null, "\\d+");
		doTest("Bug IDs: 3, 4, 5", parser);
	}

	// Utils ==================================================================

	private BugtraqParser createParser(@Nullable String filterRegex, @Nullable String linkRegex, @NotNull String idRegex) throws BugtraqException {
		return BugtraqParser.createInstance(idRegex, linkRegex, filterRegex);
	}
	
	private BugtraqParserIssueId id(int from, int to, String id) {
		return new BugtraqParserIssueId(from, to, id);
	} 

	private void doTest(String message, BugtraqParser parser, BugtraqParserIssueId... expectedIds) {
		final List<BugtraqParserIssueId> actualIds = parser.parse(message);
		assertEquals(expectedIds.length, actualIds.size());
		
		for (int index = 0; index < expectedIds.length; index++) {
			final BugtraqParserIssueId expectedId = expectedIds[index];
			final BugtraqParserIssueId actualId = actualIds.get(index);
			assertEquals(expectedId.getFrom(), actualId.getFrom());
			assertEquals(expectedId.getTo(), actualId.getTo());
			assertEquals(expectedId.getId(), actualId.getId());
		}
	}
}