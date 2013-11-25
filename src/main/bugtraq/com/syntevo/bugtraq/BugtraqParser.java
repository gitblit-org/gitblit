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

import java.util.*;
import java.util.regex.*;

import org.jetbrains.annotations.*;

final class BugtraqParser {

	// Static =================================================================

	@NotNull
	public static BugtraqParser createInstance(@NotNull List<String> regexs) throws BugtraqException {
		try {
			return new BugtraqParser(regexs);
		}
		catch (PatternSyntaxException ex) {
			throw new BugtraqException(ex);
		}
	}

	// Fields =================================================================

	private final List<Pattern> patterns;

	// Setup ==================================================================

	private BugtraqParser(List<String> regexs) {
		this.patterns = new ArrayList<Pattern>();

		for (String regex : regexs) {
			patterns.add(compilePatternSafe(regex));
		}
	}

	// Accessing ==============================================================

	@Nullable
	public List<BugtraqParserIssueId> parse(@NotNull String message) {
		List<Part> parts = new ArrayList<Part>();
		parts.add(new Part(message, 0, message.length() - 1));

		boolean firstMatch = false;

		for (Pattern pattern : patterns) {
			final List<Part> newParts = new ArrayList<Part>();
			for (Part part : parts) {
				final Matcher matcher = pattern.matcher(part.text);
				while (matcher.find()) {
					firstMatch = true;
					if (matcher.groupCount() == 0) {
						addNewPart(part, matcher, 0, newParts);
					}
					else {
						addNewPart(part, matcher, 1, newParts);
					}
				}
			}

			parts = newParts;
			if (parts.isEmpty()) {
				parts = null;
				break;
			}
		}

		if (!firstMatch) {
			return null;
		}

		if (parts == null) {
			parts = new ArrayList<Part>();
		}

		final List<BugtraqParserIssueId> ids = new ArrayList<BugtraqParserIssueId>();
		for (Part part : parts) {
			final BugtraqParserIssueId id = new BugtraqParserIssueId(part.from, part.to, part.text);
			if (ids.size() > 0) {
				final BugtraqParserIssueId lastId = ids.get(ids.size() - 1);
				if (id.getFrom() <= lastId.getTo()) {
					continue;
				}
			}

			ids.add(id);
		}

		return ids;
	}

	// Utils ==================================================================

	private static void addNewPart(Part part, Matcher matcher, int group, List<Part> newParts) {
		final int textStart = matcher.start(group) + part.from;
		final int textEnd = matcher.end(group) - 1 + part.from;
		if (textEnd < 0) {
			return;
		}

		final Part newPart = new Part(matcher.group(group), textStart, textEnd);
		newParts.add(newPart);
	}

	private static Pattern compilePatternSafe(String pattern) throws PatternSyntaxException {
		return Pattern.compile(pattern);
	}

	// Inner Classes ==========================================================

	private static class Part {

		private final int from;
		private final int to;
		private final String text;

		public Part(String text, int from, int to) {
			this.text = text;
			this.from = from;
			this.to = to;
		}
	}
}