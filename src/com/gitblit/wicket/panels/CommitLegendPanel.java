package com.gitblit.wicket.panels;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;

import com.gitblit.utils.JGitUtils;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.models.PathModel.PathChangeModel;

public class CommitLegendPanel extends Panel {

	private static final long serialVersionUID = 1L;

	public CommitLegendPanel(String id, List<PathChangeModel> paths) {
		super(id);
		final Map<ChangeType, AtomicInteger> stats = JGitUtils.getChangedPathsStats(paths);		
		ListDataProvider<ChangeType> legendDp = new ListDataProvider<ChangeType>(new ArrayList<ChangeType>(stats.keySet()));
		DataView<ChangeType> legendsView = new DataView<ChangeType>("legend", legendDp) {
			private static final long serialVersionUID = 1L;

			public void populateItem(final Item<ChangeType> item) {
				ChangeType entry = item.getModelObject();

				Label changeType = new Label("changeType", "");
				WicketUtils.setChangeTypeCssClass(changeType, entry);
				item.add(changeType);
				int count = stats.get(entry).intValue();
				String description  = "";
				switch(entry) {
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
		add(legendsView);
	}
}