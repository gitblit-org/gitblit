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
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.List;

import javax.swing.DefaultComboBoxModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import com.gitblit.Constants;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.SyndicatedEntryModel;
import com.gitblit.utils.StringUtils;

/**
 * The search dialog allows searching of a repository branch. This matches the
 * search implementation of the site.
 * 
 * @author James Moger
 * 
 */
public class SearchDialog extends JFrame {

	private static final long serialVersionUID = 1L;

	private final GitblitClient gitblit;

	private SyndicatedEntryTableModel tableModel;

	private HeaderPanel header;

	private JTable table;

	private JComboBox repositorySelector;

	private DefaultComboBoxModel branchChoices;

	private JComboBox branchSelector;

	private JComboBox searchTypeSelector;

	private JTextField searchFragment;

	private JComboBox maxHitsSelector;

	private int page;

	private JButton prev;

	private JButton next;

	public SearchDialog(GitblitClient gitblit) {
		super();
		this.gitblit = gitblit;
		setTitle(Translation.get("gb.search"));
		setIconImage(new ImageIcon(getClass().getResource("/gitblt-favicon.png")).getImage());
		initialize();
		setSize(900, 400);
	}

	private void initialize() {

		prev = new JButton("<");
		prev.setToolTipText(Translation.get("gb.pagePrevious"));
		prev.setEnabled(false);
		prev.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				search(--page);
			}
		});

		next = new JButton(">");
		next.setToolTipText(Translation.get("gb.pageNext"));
		next.setEnabled(false);
		next.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				search(++page);
			}
		});

		final JButton search = new JButton(Translation.get("gb.search"));
		search.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				search(0);
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

		JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, Utils.MARGIN, 0));
		controls.add(viewCommit);
		controls.add(viewCommitDiff);
		controls.add(viewTree);

		NameRenderer nameRenderer = new NameRenderer();
		tableModel = new SyndicatedEntryTableModel();
		header = new HeaderPanel(Translation.get("gb.search"), "search-icon.png");
		table = Utils.newTable(tableModel, Utils.DATE_FORMAT);

		String name = table.getColumnName(SyndicatedEntryTableModel.Columns.Author.ordinal());
		table.setRowHeight(nameRenderer.getFont().getSize() + 8);
		table.getColumn(name).setCellRenderer(nameRenderer);
		name = table.getColumnName(SyndicatedEntryTableModel.Columns.Repository.ordinal());
		table.getColumn(name).setCellRenderer(nameRenderer);

		name = table.getColumnName(SyndicatedEntryTableModel.Columns.Branch.ordinal());
		table.getColumn(name).setCellRenderer(new BranchRenderer());

		name = table.getColumnName(SyndicatedEntryTableModel.Columns.Message.ordinal());
		table.getColumn(name).setCellRenderer(new MessageRenderer());

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

		repositorySelector = new JComboBox(gitblit.getRepositories().toArray());
		repositorySelector.setRenderer(nameRenderer);
		repositorySelector.setForeground(nameRenderer.getForeground());
		repositorySelector.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent event) {
				// repopulate the branch list based on repository selection
				// preserve branch selection, if possible
				String selectedBranch = null;
				if (branchSelector.getSelectedIndex() > -1) {
					selectedBranch = branchSelector.getSelectedItem().toString();
				}
				updateBranches();
				if (selectedBranch != null) {
					if (branchChoices.getIndexOf(selectedBranch) > -1) {
						branchChoices.setSelectedItem(selectedBranch);
					}
				}
			}
		});

		branchChoices = new DefaultComboBoxModel();
		branchSelector = new JComboBox(branchChoices);
		branchSelector.setRenderer(new BranchRenderer());

		searchTypeSelector = new JComboBox(Constants.SearchType.values());
		searchTypeSelector.setSelectedItem(Constants.SearchType.COMMIT);

		maxHitsSelector = new JComboBox(new Integer[] { 25, 50, 75, 100 });
		maxHitsSelector.setSelectedIndex(0);

		searchFragment = new JTextField(25);
		searchFragment.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent event) {
				search(0);
			}
		});

		JPanel northControls = new JPanel(new FlowLayout(FlowLayout.LEFT, Utils.MARGIN, 0));
		northControls.add(new JLabel(Translation.get("gb.repository")));
		northControls.add(repositorySelector);
		northControls.add(new JLabel(Translation.get("gb.branch")));
		northControls.add(branchSelector);
		northControls.add(new JLabel(Translation.get("gb.type")));
		northControls.add(searchTypeSelector);
		northControls.add(new JLabel(Translation.get("gb.maxHits")));
		northControls.add(maxHitsSelector);
		northControls.add(searchFragment);
		northControls.add(search);
		northControls.add(prev);
		northControls.add(next);

		JPanel northPanel = new JPanel(new BorderLayout(0, Utils.MARGIN));
		northPanel.add(header, BorderLayout.NORTH);
		northPanel.add(northControls, BorderLayout.CENTER);

		JPanel contentPanel = new JPanel() {

			private static final long serialVersionUID = 1L;

			@Override
			public Insets getInsets() {
				return Utils.INSETS;
			}
		};
		contentPanel.setLayout(new BorderLayout(Utils.MARGIN, Utils.MARGIN));
		contentPanel.add(northPanel, BorderLayout.NORTH);
		contentPanel.add(new JScrollPane(table), BorderLayout.CENTER);
		contentPanel.add(controls, BorderLayout.SOUTH);
		setLayout(new BorderLayout());
		add(contentPanel, BorderLayout.CENTER);
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowOpened(WindowEvent event) {
				searchFragment.requestFocus();
			}

			@Override
			public void windowActivated(WindowEvent event) {
				searchFragment.requestFocus();
			}
		});
	}

	public void selectRepository(RepositoryModel repository) {
		repositorySelector.setSelectedItem(repository);
	}

	private void updateBranches() {
		String repository = null;
		if (repositorySelector.getSelectedIndex() > -1) {
			repository = repositorySelector.getSelectedItem().toString();
		}
		List<String> branches = gitblit.getBranches(repository);
		branchChoices.removeAllElements();
		for (String branch : branches) {
			branchChoices.addElement(branch);
		}
	}

	protected void search(final int page) {
		this.page = page;
		final String repository = repositorySelector.getSelectedItem().toString();
		final String branch = branchSelector.getSelectedIndex() > -1 ? branchSelector
				.getSelectedItem().toString() : null;
		final Constants.SearchType searchType = (Constants.SearchType) searchTypeSelector
				.getSelectedItem();
		final String fragment = searchFragment.getText();
		final int maxEntryCount = maxHitsSelector.getSelectedIndex() > -1 ? ((Integer) maxHitsSelector
				.getSelectedItem()) : -1;

		if (StringUtils.isEmpty(fragment)) {
			return;
		}
		SwingWorker<List<SyndicatedEntryModel>, Void> worker = new SwingWorker<List<SyndicatedEntryModel>, Void>() {
			@Override
			protected List<SyndicatedEntryModel> doInBackground() throws IOException {
				return gitblit
						.search(repository, branch, fragment, searchType, maxEntryCount, page);
			}

			@Override
			protected void done() {
				try {
					List<SyndicatedEntryModel> results = get();
					updateTable(true, fragment, results);
				} catch (Throwable t) {
					Utils.showException(SearchDialog.this, t);
				}
			}
		};
		worker.execute();
	}

	protected void updateTable(boolean pack, String fragment, List<SyndicatedEntryModel> entries) {
		tableModel.entries.clear();
		tableModel.entries.addAll(entries);
		tableModel.fireTableDataChanged();
		setTitle(Translation.get("gb.search") + ": " + fragment + " (" + entries.size()
				+ (page > 0 ? (", pg " + (page + 1)) : "") + ")");
		header.setText(getTitle());
		if (pack) {
			Utils.packColumns(table, Utils.MARGIN);
		}
		table.scrollRectToVisible(new Rectangle(table.getCellRect(0, 0, true)));

		// update pagination buttons
		int maxHits = (Integer) maxHitsSelector.getSelectedItem();
		next.setEnabled(entries.size() == maxHits);
		prev.setEnabled(page > 0);
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
}
