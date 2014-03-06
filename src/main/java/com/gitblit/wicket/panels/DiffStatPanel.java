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
package com.gitblit.wicket.panels;

import java.text.MessageFormat;

import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.panel.Panel;

import com.gitblit.utils.DiffUtils;
import com.gitblit.utils.DiffUtils.NormalizedDiffStat;
import com.gitblit.wicket.WicketUtils;

/**
 * Display a diffstat.
 *
 * @author James Moger
 *
 */
public class DiffStatPanel extends Panel {

	private static final long serialVersionUID = 1L;

	final int total;

	final int insertions;

	final int deletions;

	final boolean inline;

	public DiffStatPanel(String wicketId, int insertions, int deletions) {
		this(wicketId, insertions, deletions, false);
	}

	public DiffStatPanel(String wicketId, int insertions, int deletions, boolean inline) {
		super(wicketId);
		this.insertions = insertions;
		this.deletions = deletions;
		this.total = insertions + deletions;
		this.inline = inline;
	}

	@Override
	protected void onInitialize() {
		super.onInitialize();

		final String diffStat = MessageFormat.format(getString("gb.diffStat"), "" + insertions, "" + deletions);
		WicketUtils.setHtmlTooltip(this, diffStat);

		final NormalizedDiffStat n = DiffUtils.normalizeDiffStat(5, insertions, deletions);

		final String segment;
		if (inline) {
			segment = "&#9679;";
		} else {
			segment = "&#9632;";
		}

		add(new Label("total", String.valueOf(total)));
		add(new Label("insertions", timesRepeat(n.insertions, segment)).setEscapeModelStrings(false).setVisible(n.insertions > 0));
		add(new Label("deletions", timesRepeat(n.deletions, segment)).setEscapeModelStrings(false).setVisible(n.deletions > 0));
		add(new Label("blank", timesRepeat(n.blanks, segment)).setEscapeModelStrings(false).setVisible(n.blanks > 0));

		if (inline) {
			WicketUtils.setCssClass(this, "diffstat-inline");
		} else {
			WicketUtils.setCssClass(this, "diffstat");
		}

		setVisible(total > 0);
	}

	String timesRepeat(int cnt, String s) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < cnt; i++) {
			sb.append(s);
		}
		return sb.toString();
	}
}
