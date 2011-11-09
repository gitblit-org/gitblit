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
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
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

import com.gitblit.models.FeedEntryModel;
import com.gitblit.models.FeedModel;
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

	private final String ALL = "*";

	private FeedEntryTableModel tableModel;

	private TableRowSorter<FeedEntryTableModel> defaultSorter;

	private HeaderPanel header;

	private JTable table;

	private DefaultComboBoxModel repositoryChoices;

	private JComboBox repositorySelector;

	private DefaultComboBoxModel authorChoices;

	private JComboBox authorSelector;

	private int page;

	private JButton prev;

	private JButton next;

	public FeedsPanel(GitblitClient gitblit) {
		super();
		this.gitblit = gitblit;
		initialize();
	}

	private void initialize() {

		prev = new JButton("<");
		prev.setToolTipText(Translation.get("gb.pagePrevious"));
		prev.setEnabled(false);
		prev.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				refreshFeeds(--page);
			}
		});

		next = new JButton(">");
		next.setToolTipText(Translation.get("gb.pageNext"));
		next.setEnabled(false);
		next.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				refreshFeeds(++page);
			}
		});

		JButton refreshFeeds = new JButton(Translation.get("gb.refresh"));
		refreshFeeds.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				refreshFeeds(0);
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
		tableModel = new FeedEntryTableModel();
		header = new HeaderPanel(Translation.get("gb.activity"), "feed_16x16.png");
		table = Utils.newTable(tableModel, Utils.DATE_FORMAT);
		defaultSorter = new TableRowSorter<FeedEntryTableModel>(tableModel);
		String name = table.getColumnName(FeedEntryTableModel.Columns.Author.ordinal());
		table.setRowHeight(nameRenderer.getFont().getSize() + 8);
		table.getColumn(name).setCellRenderer(nameRenderer);
		name = table.getColumnName(FeedEntryTableModel.Columns.Repository.ordinal());
		table.getColumn(name).setCellRenderer(nameRenderer);

		name = table.getColumnName(FeedEntryTableModel.Columns.Branch.ordinal());
		table.getColumn(name).setCellRenderer(new BranchRenderer());

		name = table.getColumnName(FeedEntryTableModel.Columns.Message.ordinal());
		table.getColumn(name).setCellRenderer(new MessageRenderer(gitblit));

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
		repositorySelector = new JComboBox(repositoryChoices);
		repositorySelector.setRenderer(nameRenderer);
		repositorySelector.setForeground(nameRenderer.getForeground());
		repositorySelector.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent event) {
				// repopulate the author list based on repository selection
				// preserve author selection, if possible
				String selectedAuthor = null;
				if (authorSelector.getSelectedIndex() > -1) {
					selectedAuthor = authorSelector.getSelectedItem().toString();
				}
				updateAuthors();
				if (selectedAuthor != null) {
					if (authorChoices.getIndexOf(selectedAuthor) > -1) {
						authorChoices.setSelectedItem(selectedAuthor);
					}
				}
				filterFeeds();
			}
		});
		authorChoices = new DefaultComboBoxModel();
		authorSelector = new JComboBox(authorChoices);
		authorSelector.setRenderer(nameRenderer);
		authorSelector.setForeground(nameRenderer.getForeground());
		authorSelector.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent event) {
				filterFeeds();
			}
		});
		JPanel northControls = new JPanel(new FlowLayout(FlowLayout.LEFT, Utils.MARGIN, 0));
		northControls.add(new JLabel(Translation.get("gb.repository")));
		northControls.add(repositorySelector);
		northControls.add(new JLabel(Translation.get("gb.author")));
		northControls.add(authorSelector);
//		northControls.add(prev);
//		northControls.add(next);

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

	protected void refreshFeeds(final int page) {
		this.page = page;
		GitblitWorker worker = new GitblitWorker(FeedsPanel.this, null) {
			@Override
			protected Boolean doRequest() throws IOException {
				gitblit.refreshSubscribedFeeds(page);
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
		header.setText(Translation.get("gb.activity") + " ("
				+ gitblit.getSyndicatedEntries().size() + (page > 0 ? (", pg " + (page + 1)) : "")
				+ ")");
		if (pack) {
			Utils.packColumns(table, Utils.MARGIN);
		}
		table.scrollRectToVisible(new Rectangle(table.getCellRect(0, 0, true)));

		if (page == 0) {
			// determine unique repositories
			Set<String> uniqueRepositories = new HashSet<String>();
			for (FeedEntryModel entry : tableModel.entries) {
				uniqueRepositories.add(entry.repository);
			}

			// repositories
			List<String> sortedRespositories = new ArrayList<String>(uniqueRepositories);
			StringUtils.sortRepositorynames(sortedRespositories);
			repositoryChoices.removeAllElements();
			repositoryChoices.addElement(ALL);
			for (String repo : sortedRespositories) {
				repositoryChoices.addElement(repo);
			}
		}

		// update pagination buttons
		next.setEnabled(tableModel.entries.size() > 0);
		prev.setEnabled(page > 0);
	}

	private void updateAuthors() {
		String repository = ALL;
		if (repositorySelector.getSelectedIndex() > -1) {
			repository = repositorySelector.getSelectedItem().toString();
		}

		// determine unique repositories and authors
		Set<String> uniqueAuthors = new HashSet<String>();
		for (FeedEntryModel entry : tableModel.entries) {
			if (repository.equals(ALL) || entry.repository.equalsIgnoreCase(repository)) {
				uniqueAuthors.add(entry.author);
			}
		}
		// authors
		List<String> sortedAuthors = new ArrayList<String>(uniqueAuthors);
		Collections.sort(sortedAuthors);
		authorChoices.removeAllElements();
		authorChoices.addElement(ALL);
		for (String author : sortedAuthors) {
			authorChoices.addElement(author);
		}
	}

	protected FeedEntryModel getSelectedSyndicatedEntry() {
		int viewRow = table.getSelectedRow();
		int modelRow = table.convertRowIndexToModel(viewRow);
		FeedEntryModel entry = tableModel.get(modelRow);
		return entry;
	}

	protected void viewCommit() {
		FeedEntryModel entry = getSelectedSyndicatedEntry();
		Utils.browse(entry.link);
	}

	protected void viewCommitDiff() {
		FeedEntryModel entry = getSelectedSyndicatedEntry();
		Utils.browse(entry.link.replace("/commit/", "/commitdiff/"));
	}

	protected void viewTree() {
		FeedEntryModel entry = getSelectedSyndicatedEntry();
		Utils.browse(entry.link.replace("/commit/", "/tree/"));
	}

	protected void filterFeeds() {
		final String repository;
		if (repositorySelector.getSelectedIndex() > -1) {
			repository = repositorySelector.getSelectedItem().toString();
		} else {
			repository = ALL;
		}

		final String author;
		if (authorSelector.getSelectedIndex() > -1) {
			author = authorSelector.getSelectedItem().toString();
		} else {
			author = ALL;
		}

		if (repository.equals(ALL) && author.equals(ALL)) {
			table.setRowSorter(defaultSorter);
			return;
		}
		final int repositoryIndex = FeedEntryTableModel.Columns.Repository.ordinal();
		final int authorIndex = FeedEntryTableModel.Columns.Author.ordinal();
		RowFilter<FeedEntryTableModel, Object> containsFilter;
		if (repository.equals(ALL)) {
			// author filter
			containsFilter = new RowFilter<FeedEntryTableModel, Object>() {
				public boolean include(
						Entry<? extends FeedEntryTableModel, ? extends Object> entry) {
					return entry.getStringValue(authorIndex).equalsIgnoreCase(author);
				}
			};
		} else if (author.equals(ALL)) {
			// repository filter
			containsFilter = new RowFilter<FeedEntryTableModel, Object>() {
				public boolean include(
						Entry<? extends FeedEntryTableModel, ? extends Object> entry) {
					return entry.getStringValue(repositoryIndex).equalsIgnoreCase(repository);
				}
			};
		} else {
			// repository-author filter
			containsFilter = new RowFilter<FeedEntryTableModel, Object>() {
				public boolean include(
						Entry<? extends FeedEntryTableModel, ? extends Object> entry) {
					boolean authorMatch = entry.getStringValue(authorIndex)
							.equalsIgnoreCase(author);
					boolean repositoryMatch = entry.getStringValue(repositoryIndex)
							.equalsIgnoreCase(repository);
					return authorMatch && repositoryMatch;
				}
			};
		}
		TableRowSorter<FeedEntryTableModel> sorter = new TableRowSorter<FeedEntryTableModel>(
				tableModel);
		sorter.setRowFilter(containsFilter);
		table.setRowSorter(sorter);
	}
}
