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

import org.jetbrains.annotations.*;

public final class BugtraqFormatter {

	// Fields =================================================================

	private final BugtraqConfig config;

	// Setup ==================================================================

	public BugtraqFormatter(@NotNull BugtraqConfig config) {
		this.config = config;
	}

	// Accessing ==============================================================

	public void formatLogMessage(@NotNull String message, @NotNull OutputHandler outputHandler) {
		final SortedSet<IssueId> allIds = new TreeSet<IssueId>(new Comparator<IssueId>() {
			@Override
			public int compare(IssueId o1, IssueId o2) {
				final int from1 = o1.id.getFrom();
				final int from2 = o2.id.getFrom();
				return from1 > from2 ? +1 : from1 < from2 ? -1 : 0;
			}
		});

		for (BugtraqConfigEntry configEntry : config.getEntries()) {
			for (BugtraqEntry entry : configEntry.getEntries()) {
				final List<BugtraqParserIssueId> ids = entry.getParser().parse(message);
				for (BugtraqParserIssueId id : ids) {
					allIds.add(new IssueId(entry, id));
				}
			}
		}

		int lastIdEnd = -1;
		for (IssueId issueId : allIds) {
			final BugtraqParserIssueId id = issueId.id;
			if (id.getFrom() <= lastIdEnd) {
				continue;
			}

			appendText(message.substring(lastIdEnd + 1, id.getFrom()), outputHandler);
			final String logLinkText = issueId.entry.getLogLinkText();
			final String linkText;
			if (logLinkText != null) {
				linkText = logLinkText.replace("%BUGID%", id.getId());
			}
			else {
				linkText = message.substring(id.getFrom(), id.getTo() + 1);
			}

			final String target = issueId.entry.getUrl().replace("%BUGID%", id.getId());
			outputHandler.appendLink(linkText, target);
			lastIdEnd = id.getTo();
		}

		if (lastIdEnd - 1 < message.length()) {
			appendText(message.substring(lastIdEnd + 1, message.length()), outputHandler);
		}
	}

	// Utils ==================================================================

	private static void appendText(@NotNull String message, @NotNull OutputHandler outputHandler) {
		if (message.length() == 0) {
			return;
		}

		outputHandler.appendText(message);
	}

	// Inner Classes ==========================================================

	public interface OutputHandler {
		void appendText(@NotNull String text);

		void appendLink(@NotNull String name, @NotNull String target);
	}
	
	private static class IssueId {
		private final BugtraqEntry entry;
		private final BugtraqParserIssueId id;

		private IssueId(BugtraqEntry entry, BugtraqParserIssueId id) {
			this.entry = entry;
			this.id = id;
		}
	}
}