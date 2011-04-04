package com.gitblit.wicket.panels;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.wicket.Component;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import com.gitblit.utils.JGitUtils;
import com.gitblit.wicket.WicketUtils;


public class RefsPanel extends Panel {

	private static final long serialVersionUID = 1L;

	public RefsPanel(String id, Repository r, RevCommit c) {
		this(id, c, JGitUtils.getAllRefs(r));
	}
	
	public RefsPanel(String id, RevCommit c, Map<ObjectId, List<String>> refs) {
		super(id);
		List<String> refNames = refs.get(c.getId());
		if (refNames == null) {
			refNames = new ArrayList<String>();
		}
		Collections.sort(refNames);
		ListDataProvider<String> refsDp = new ListDataProvider<String>(refNames);
		DataView<String> refsView = new DataView<String>("ref", refsDp) {
			private static final long serialVersionUID = 1L;
			public void populateItem(final Item<String> item) {
				String entry = item.getModelObject();
				Component c = null;
				if (entry.startsWith(Constants.R_HEADS)) {
					// local head
					c = new Label("refName", entry.substring(Constants.R_HEADS.length()));
					WicketUtils.setCssClass(c, "head");
				} else if (entry.startsWith(Constants.R_REMOTES)) {
					// remote head
					c = new Label("refName", entry.substring(Constants.R_REMOTES.length()));
					WicketUtils.setCssClass(c, "ref");
				} else if (entry.startsWith(Constants.R_TAGS)) {
					// tag
					c = new Label("refName", entry.substring(Constants.R_TAGS.length()));
					WicketUtils.setCssClass(c, "tag");
				} else {
					// other
					c = new Label("refName", entry);					
				}
				WicketUtils.setHtmlTitle(c, entry);
				item.add(c);
			}
		};
		add(refsView);
	}
}