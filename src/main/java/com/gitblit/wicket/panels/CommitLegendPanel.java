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
package com.gitblit.wicket.panels;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.models.PathModel.PathChangeModel;
import com.gitblit.wicket.WicketUtils;

public class CommitLegendPanel extends Panel {

	private static final long serialVersionUID = 1L;
	static final Logger LOGGER = LoggerFactory.getLogger(CommitLegendPanel.class);
	static final boolean TRACE = LOGGER.isTraceEnabled();
	static final boolean DEBUG = LOGGER.isDebugEnabled();
	
	public CommitLegendPanel(String id, List<PathChangeModel> paths) { this(id, paths, false) ;};
	/** Creates
	@param id wicket idendtifier
	@param paths list of changed paths
	@param approx true if <code>paths</code> do not represent a full set of changes
		present in commit due to, for an example, some limits.
	*/
	public CommitLegendPanel(String id, List<PathChangeModel> paths, final boolean approx) {
		super(id);
		if (TRACE) LOGGER.trace("new CommitLegendPanel("+id+",paths.size()="+paths.size()+", approx="+approx+")");
		final Map<ChangeType, AtomicInteger> stats = getChangedPathsStats(paths);
		ListDataProvider<ChangeType> legendDp = new ListDataProvider<ChangeType>(
				new ArrayList<ChangeType>(stats.keySet()));
		DataView<ChangeType> legendsView = new DataView<ChangeType>("legend", legendDp) {
			private static final long serialVersionUID = 1L;

			@Override
			public void populateItem(final Item<ChangeType> item) {
				ChangeType entry = item.getModelObject();

				Label changeType = new Label("changeType", "");
				WicketUtils.setChangeTypeCssClass(changeType, entry);
				item.add(changeType);
				int count = stats.get(entry).intValue();
				String description = "";
				switch (entry) {
				case ADD:
					description = MessageFormat.format(getString("gb.filesAdded"), count);
					break;
				case MODIFY:
					description = MessageFormat.format(getString("gb.filesModified"), count);
					break;
				case DELETE:
					description = MessageFormat.format(getString("gb.filesDeleted"), count);
					break;
				case COPY:
					description = MessageFormat.format(getString("gb.filesCopied"), count);
					break;
				case RENAME:
					description = MessageFormat.format(getString("gb.filesRenamed"), count);
					break;
				}
				item.add(new Label("description", description));
			}
		};
		//Note: There is a known problem which I can't handle, that is "moreChanges" do appear
		//on web BEFORE "legend" if in html is AFTER "description".
		add(new Label("moreChanges",getString("gb.CommitLegendPanel.moreChanges")).setVisible(approx));
		add(legendsView);		
		
	}
	
	protected Map<ChangeType, AtomicInteger> getChangedPathsStats(List<PathChangeModel> paths) {
		Map<ChangeType, AtomicInteger> stats = new HashMap<ChangeType, AtomicInteger>();
		for (PathChangeModel path : paths) {
			if (!stats.containsKey(path.changeType)) {
				stats.put(path.changeType, new AtomicInteger(0));
			}
			stats.get(path.changeType).incrementAndGet();
		}
		return stats;
	}
}