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

public final class BugtraqConfigEntry {

	// Fields =================================================================

	private final String url;
	private final List<String> projects;
	private final List<BugtraqEntry> entries;

	// Setup ==================================================================

	public BugtraqConfigEntry(@NotNull String url, @NotNull String logIdRegex, @Nullable String logLinkRegex, @Nullable String logFilterRegex, @Nullable String logLinkText, @Nullable List<String> projects) throws BugtraqException {
		this.url = url;
		this.projects = projects;
		this.entries = new ArrayList<>();
		if (projects == null) {
			entries.add(new BugtraqEntry(url, logIdRegex, logLinkRegex, logFilterRegex, logLinkText));
		}
		else {
			for (String project : projects) {
				final String projectUrl = this.url.replace("%PROJECT%", project);
				final String projectLogIdRegex = logIdRegex.replace("%PROJECT%", project);
				final String projectLogLinkRegex = logLinkRegex != null ? logLinkRegex.replace("%PROJECT%", project) : null;
				final String projectLogFilterRegex = logFilterRegex != null ? logFilterRegex.replace("%PROJECT%", project) : null;
				final String projectLogLinkText = logLinkText != null ? logLinkText.replace("%PROJECT%", project) : null;
				entries.add(new BugtraqEntry(projectUrl, projectLogIdRegex, projectLogLinkRegex, projectLogFilterRegex, projectLogLinkText));
			}
		}
	}

	// Accessing ==============================================================

	@NotNull
	public String getUrl() {
		return url;
	}

	@Nullable
	public List<String> getProjects() {
		return projects != null ? Collections.unmodifiableList(projects) : null;
	}

	public List<BugtraqEntry> getEntries() {
		return entries;
	}
}