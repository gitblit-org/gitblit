/*
 * Copyright 2013 gitblit.com.
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
package com.gitblit.utils;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.models.RepositoryModel;
import com.syntevo.bugtraq.BugtraqConfig;
import com.syntevo.bugtraq.BugtraqFormatter;
import com.syntevo.bugtraq.BugtraqFormatter.OutputHandler;

public class MessageProcessor {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final IStoredSettings settings;

	public MessageProcessor(IStoredSettings settings) {
		this.settings = settings;
	}

	/**
	 * Returns an html version of the commit message with any global or
	 * repository-specific regular expression substitution applied.
	 *
	 * This method uses the preferred renderer to transform the commit message.
	 *
	 * @param repository
	 * @param model
	 * @param text
	 * @return html version of the commit message
	 */
	public String processCommitMessage(Repository repository, RepositoryModel model, String text) {
		switch (model.commitMessageRenderer) {
		case MARKDOWN:
			try {
				String prepared = processCommitMessageRegex(repository, model.name, text);
				return MarkdownUtils.transformMarkdown(prepared);
			} catch (Exception e) {
				logger.error("Failed to render commit message as markdown", e);
			}
			break;
		default:
			// noop
			break;
		}

		return processPlainCommitMessage(repository, model.name, text);
	}

	/**
	 * Returns an html version of the commit message with any global or
	 * repository-specific regular expression substitution applied.
	 *
	 * This method assumes the commit message is plain text.
	 *
	 * @param repository
	 * @param repositoryName
	 * @param text
	 * @return html version of the commit message
	 */
	public String processPlainCommitMessage(Repository repository, String repositoryName, String text) {
		String html = StringUtils.escapeForHtml(text, false);
		html = processCommitMessageRegex(repository, repositoryName, html);
		return StringUtils.breakLinesForHtml(html);

	}

	/**
	 * Apply globally or per-repository specified regex substitutions to the
	 * commit message.
	 *
	 * @param repository
	 * @param repositoryName
	 * @param text
	 * @return the processed commit message
	 */
	protected String processCommitMessageRegex(Repository repository, String repositoryName, String text) {
		Map<String, String> map = new HashMap<String, String>();
		// global regex keys
		if (settings.getBoolean(Keys.regex.global, false)) {
			for (String key : settings.getAllKeys(Keys.regex.global)) {
				if (!key.equals(Keys.regex.global)) {
					String subKey = key.substring(key.lastIndexOf('.') + 1);
					map.put(subKey, settings.getString(key, ""));
				}
			}
		}

		// repository-specific regex keys
		List<String> keys = settings.getAllKeys(Keys.regex._ROOT + "."
				+ repositoryName.toLowerCase());
		for (String key : keys) {
			String subKey = key.substring(key.lastIndexOf('.') + 1);
			map.put(subKey, settings.getString(key, ""));
		}

		for (Entry<String, String> entry : map.entrySet()) {
			String definition = entry.getValue().trim();
			String[] chunks = definition.split("!!!");
			if (chunks.length == 2) {
				text = text.replaceAll(chunks[0], chunks[1]);
			} else {
				logger.warn(entry.getKey()
						+ " improperly formatted.  Use !!! to separate match from replacement: "
						+ definition);
			}
		}

		try {
			// parse bugtraq repo config
			BugtraqConfig config = BugtraqConfig.read(repository);
			if (config != null) {
				BugtraqFormatter formatter = new BugtraqFormatter(config);
				StringBuilder sb = new StringBuilder();
				formatter.formatLogMessage(text, new BugtraqOutputHandler(sb));
				text = sb.toString();
			}
		} catch (IOException e) {
			logger.error(MessageFormat.format("Bugtraq config for {0} is invalid!", repositoryName), e);
		} catch (ConfigInvalidException e) {
			logger.error(MessageFormat.format("Bugtraq config for {0} is invalid!", repositoryName), e);
		}

		return text;
	}

	private class BugtraqOutputHandler implements OutputHandler {

		final StringBuilder sb;

		BugtraqOutputHandler(StringBuilder sb) {
			this.sb = sb;
		}

		@Override
		public void appendText(String text) {
			sb.append(text);
		}

		@Override
		public void appendLink(String name, String target) {
			sb.append(MessageFormat.format("<a class=\"bugtraq\" href=\"{1}\" target=\"_blank\">{0}</a>", name, target));
		}
	}
}
