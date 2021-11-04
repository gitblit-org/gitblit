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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
	static final Logger LOGGER = LoggerFactory.getLogger(DiffStatPanel.class);
	static final boolean TRACE = LOGGER.isTraceEnabled();
	static final boolean DEBUG = LOGGER.isDebugEnabled();
	

	final int total;

	final int insertions;

	final int deletions;

	final boolean inline;
	
	final boolean approx;
	
	public DiffStatPanel(String wicketId, int insertions, int deletions) {
		this(wicketId, insertions, deletions, false);
	}
	public DiffStatPanel(String wicketId, int insertions, int deletions, boolean inline) {
		this(wicketId, insertions, deletions, inline,false);
	};
	/** Creates
	@param wicketId wicket identifier
	@param insertions total number of insertions within a commit. 
	@param deletions total number of deletions within a commit.
	@param inline true if panel is inline with commit file and represents stats of single file
		or flase if used in header and represents stats of commit.
	@param approx true if number of insertions and deletions is approximate, ie because
		commit processing was limited in server settings.
	*/	
	public DiffStatPanel(String wicketId, int insertions, int deletions, boolean inline, boolean approx) {
		super(wicketId);
		if (TRACE) LOGGER.trace("new DiffStatPanel("+wicketId+",insertions="+insertions+",deletions="+deletions+",inline="+inline+",approx="+approx);
		this.approx = approx;
		this.insertions = insertions;
		this.deletions = deletions;
		this.total = insertions + deletions;
		this.inline = inline;
	}

	@Override
	protected void onInitialize() {
		if (TRACE) LOGGER.trace("DiffStatPanel.onInitialize() ENTER");
		
		super.onInitialize();
		
		final String diffStat = MessageFormat.format(getString("gb.diffStat"), "" + insertions, "" + deletions, approx ? 1 :0 );
		WicketUtils.setHtmlTooltip(this, diffStat);

		final NormalizedDiffStat n = DiffUtils.normalizeDiffStat(5, insertions, deletions);

		final String segment;
		if (inline) {
			segment = "&#9679;";
		} else {
			segment = "&#9632;";
		}
		
		add(new Label("total",MessageFormat.format(getString("gb.diffStat.total"),String.valueOf(total), approx ? 1 :0)));		
		add(new Label("insertions", timesRepeat(n.insertions, segment)).setEscapeModelStrings(false).setVisible(n.insertions > 0));
		add(new Label("deletions", timesRepeat(n.deletions, segment)).setEscapeModelStrings(false).setVisible(n.deletions > 0));
		add(new Label("blank", timesRepeat(n.blanks, segment)).setEscapeModelStrings(false).setVisible(n.blanks > 0));

		if (inline) {
			WicketUtils.setCssClass(this, "diffstat-inline");
		} else {
			WicketUtils.setCssClass(this, "diffstat");
		}

		setVisible(total > 0);
		
		if (TRACE) LOGGER.trace("DiffStatPanel.onInitialize() LEAVE");
	}

	String timesRepeat(int cnt, String s) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < cnt; i++) {
			sb.append(s);
		}
		return sb.toString();
	}
}
