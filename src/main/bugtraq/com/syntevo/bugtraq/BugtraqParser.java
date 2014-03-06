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
	public static BugtraqParser createInstance(@NotNull String idRegex, @Nullable String linkRegex, @Nullable String filterRegex) throws BugtraqException {
		try {
			return new BugtraqParser(idRegex, linkRegex, filterRegex);
		}
		catch (PatternSyntaxException ex) {
			throw new BugtraqException(ex);
		}
	}

	// Fields =================================================================

	private final Pattern idPattern;
	private final Pattern linkPattern;
	private final Pattern filterPattern;

	// Setup ==================================================================

	private BugtraqParser(@NotNull String idRegex, @Nullable String linkRegex, @Nullable String filterRegex) {
		idPattern = compilePatternSafe(idRegex);
		linkPattern = linkRegex != null ? compilePatternSafe(linkRegex) : null;
		filterPattern = filterRegex != null ? compilePatternSafe(filterRegex) : null;
	}

	// Accessing ==============================================================

	@Nullable
	public List<BugtraqParserIssueId> parse(@NotNull String message) {
		List<Part> parts = new ArrayList<Part>();
		parts.add(new Part(message, 0, message.length() - 1));

		if (filterPattern != null) {
			parts = collectParts(parts, filterPattern);
		}

		if (linkPattern != null) {
			parts = collectParts(parts, linkPattern);
		}

		final List<BugtraqParserIssueId> ids = new ArrayList<BugtraqParserIssueId>();
		for (final Part part : parts) {
			final Matcher matcher = idPattern.matcher(part.text);
			while (matcher.find()) {
				final Part subPart = createSubPart(part, matcher, matcher.groupCount() == 0 ? 0 : 1);
				if (subPart == null) {
					continue;
				}
				
				final BugtraqParserIssueId id;
				if (linkPattern == null) {
					id = new BugtraqParserIssueId(subPart.from, subPart.to, subPart.text);
				}
				else {
					if (matcher.find()) {
						// If we are using links, the last pattern (link) must produce exactly one id.
						continue;
					}
					
					id = new BugtraqParserIssueId(part.from, part.to, subPart.text);
				}

				if (ids.size() > 0) {
					final BugtraqParserIssueId lastId = ids.get(ids.size() - 1);
					if (id.getFrom() <= lastId.getTo()) {
						continue;
					}
				}
				
				ids.add(id);
			}
		}

		return ids;
	}

	// Utils ==================================================================

	private static List<Part> collectParts(@NotNull List<Part> mainParts, @NotNull Pattern pattern) {
		final List<Part> subParts = new ArrayList<Part>();
		for (final Part part : mainParts) {
			final Matcher matcher = pattern.matcher(part.text);
			while (matcher.find()) {
				final Part newPart = createSubPart(part, matcher, matcher.groupCount() == 0 ? 0 : 1);
				if (newPart != null) {
					subParts.add(newPart);
				}
			}
		}

		return subParts;
	}

	@Nullable
	private static Part createSubPart(Part part, Matcher matcher, int group) {
		final int textStart = matcher.start(group) + part.from;
		final int textEnd = matcher.end(group) - 1 + part.from;
		if (textEnd < 0) {
			return null;
		}

		return new Part(matcher.group(group), textStart, textEnd);
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