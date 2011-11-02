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
package com.gitblit.client;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.RowFilter;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableRowSorter;

import com.gitblit.Constants.RpcRequest;
import com.gitblit.models.FeedModel;
import com.gitblit.models.SyndicatedEntryModel;
import com.gitblit.utils.StringUtils;

/**
 * RSS Feeds Panel displays recent entries and launches the browser to view the
 * commit. commitdiff, or tree of a commit.
 * 
 * @author James Moger
 * 
 */
public abstract class FeedsPanel extends JPanel {

	private static final long serialVersionUID = 1L;

	private final GitblitClient gitblit;

	private final String ALL = "ALL";

	private SyndicatedEntryTableModel tableModel;

	private TableRowSorter<SyndicatedEntryTableModel> defaultSorter;

	private HeaderPanel header;

	private JTable table;

	private DefaultComboBoxModel repositoryChoices;

	public FeedsPanel(GitblitClient gitblit) {
		super();
		this.gitblit = gitblit;
		initialize();
	}

	private void initialize() {
		JButton refreshFeeds = new JButton(Translation.get("gb.refresh"));
		refreshFeeds.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				refreshFeeds();
			}
		});

		final JButton viewCommit = new JButton(Translation.get("gb.view"));
		viewCommit.setEnabled(false);
		viewCommit.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				viewCommit();
			}
		});

		final JButton viewCommitDiff = new JButton(Translation.get("gb.commitdiff"));
		viewCommitDiff.setEnabled(false);
		viewCommitDiff.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				viewCommitDiff();
			}
		});

		final JButton viewTree = new JButton(Translation.get("gb.tree"));
		viewTree.setEnabled(false);
		viewTree.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				viewTree();
			}
		});

		JButton subscribeFeeds = new JButton(Translation.get("gb.subscribe") + "...");
		subscribeFeeds.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				subscribeFeeds(gitblit.getAvailableFeeds());
			}
		});

		JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, Utils.MARGIN, 0));
		controls.add(refreshFeeds);
		controls.add(subscribeFeeds);
		controls.add(viewCommit);
		controls.add(viewCommitDiff);
		controls.add(viewTree);

		NameRenderer nameRenderer = new NameRenderer();
		tableModel = new SyndicatedEntryTableModel();
		header = new HeaderPanel(Translation.get("gb.timeline"), "feed_16x16.png");
		table = Utils.newTable(tableModel, Utils.DATE_FORMAT);
		defaultSorter = new TableRowSorter<SyndicatedEntryTableModel>(tableModel);
		String name = table.getColumnName(SyndicatedEntryTableModel.Columns.Author.ordinal());
		table.setRowHeight(nameRenderer.getFont().getSize() + 8);
		table.getColumn(name).setCellRenderer(nameRenderer);
		name = table.getColumnName(SyndicatedEntryTableModel.Columns.Repository.ordinal());
		table.getColumn(name).setCellRenderer(nameRenderer);

		name = table.getColumnName(SyndicatedEntryTableModel.Columns.Branch.ordinal());
		table.getColumn(name).setCellRenderer(new BranchRenderer());

		table.addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2) {
					if (e.isControlDown()) {
						viewCommitDiff();
					} else {
						viewCommit();
					}
				}
			}
		});

		table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
			@Override
			public void valueChanged(ListSelectionEvent e) {
				if (e.getValueIsAdjusting()) {
					return;
				}
				boolean singleSelection = table.getSelectedRowCount() == 1;
				viewCommit.setEnabled(singleSelection);
				viewCommitDiff.setEnabled(singleSelection);
				viewTree.setEnabled(singleSelection);
			}
		});

		repositoryChoices = new DefaultComboBoxModel();
		final JComboBox repositorySelector = new JComboBox(repositoryChoices);
		repositorySelector.setRenderer(nameRenderer);
		repositorySelector.setForeground(nameRenderer.getForeground());
		repositorySelector.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent event) {
				String repository = ALL;
				if (repositorySelector.getSelectedIndex() > -1) {
					repository = repositorySelector.getSelectedItem().toString();
				}
				filterRepositories(repository);
			}
		});
		JPanel northControls = new JPanel(new FlowLayout(FlowLayout.LEFT, Utils.MARGIN, 0));
		northControls.add(new JLabel(Translation.get("gb.repository")));
		northControls.add(repositorySelector);

		JPanel northPanel = new JPanel(new BorderLayout(0, Utils.MARGIN));
		northPanel.add(header, BorderLayout.NORTH);
		northPanel.add(northControls, BorderLayout.CENTER);

		setLayout(new BorderLayout(Utils.MARGIN, Utils.MARGIN));
		add(northPanel, BorderLayout.NORTH);
		add(new JScrollPane(table), BorderLayout.CENTER);
		add(controls, BorderLayout.SOUTH);
	}

	@Override
	public Insets getInsets() {
		return Utils.INSETS;
	}

	protected void refreshFeeds() {
		// TODO change request type here
		GitblitWorker worker = new GitblitWorker(FeedsPanel.this, RpcRequest.LIST_USERS) {
			@Override
			protected Boolean doRequest() throws IOException {
				gitblit.refreshSubscribedFeeds();
				return true;
			}

			@Override
			protected void onSuccess() {
				updateTable(false);
			}
		};
		worker.execute();
	}

	protected abstract void subscribeFeeds(List<FeedModel> feeds);

	protected void updateTable(boolean pack) {
		tableModel.entries.clear();
		tableModel.entries.addAll(gitblit.getSyndicatedEntries());
		tableModel.fireTableDataChanged();
		header.setText(Translation.get("gb.timeline") + " ("
				+ gitblit.getSyndicatedEntries().size() + ")");
		if (pack) {
			Utils.packColumns(table, Utils.MARGIN);
		}
		Set<String> uniqueRepositories = new HashSet<String>();
		for (SyndicatedEntryModel entry : tableModel.entries) {
			uniqueRepositories.add(entry.repository);
		}
		List<String> sortedRespositories = new ArrayList<String>(uniqueRepositories);
		StringUtils.sortRepositorynames(sortedRespositories);
		repositoryChoices.removeAllElements();
		repositoryChoices.addElement(ALL);
		for (String repo : sortedRespositories) {
			repositoryChoices.addElement(repo);
		}
	}

	protected SyndicatedEntryModel getSelectedSyndicatedEntry() {
		int viewRow = table.getSelectedRow();
		int modelRow = table.convertRowIndexToModel(viewRow);
		SyndicatedEntryModel entry = tableModel.get(modelRow);
		return entry;
	}

	protected void viewCommit() {
		SyndicatedEntryModel entry = getSelectedSyndicatedEntry();
		Utils.browse(entry.link);
	}

	protected void viewCommitDiff() {
		SyndicatedEntryModel entry = getSelectedSyndicatedEntry();
		Utils.browse(entry.link.replace("/commit/", "/commitdiff/"));
	}

	protected void viewTree() {
		SyndicatedEntryModel entry = getSelectedSyndicatedEntry();
		Utils.browse(entry.link.replace("/commit/", "/tree/"));
	}

	protected void filterRepositories(final String repository) {
		if (StringUtils.isEmpty(repository) || repository.equals(ALL)) {
			table.setRowSorter(defaultSorter);
			return;
		}
		final int index = SyndicatedEntryTableModel.Columns.Repository.ordinal();
		RowFilter<SyndicatedEntryTableModel, Object> containsFilter = new RowFilter<SyndicatedEntryTableModel, Object>() {
			public boolean include(
					Entry<? extends SyndicatedEntryTableModel, ? extends Object> entry) {
				return entry.getStringValue(index).equalsIgnoreCase(repository);
			}
		};
		TableRowSorter<SyndicatedEntryTableModel> sorter = new TableRowSorter<SyndicatedEntryTableModel>(
				tableModel);
		sorter.setRowFilter(containsFilter);
		table.setRowSorter(sorter);
	}
}
