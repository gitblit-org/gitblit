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
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;

import com.gitblit.models.SettingModel;
import com.gitblit.utils.StringUtils;

/**
 * This panel displays the metadata for a particular setting.
 * 
 * @author James Moger
 */
public class SettingPanel extends JPanel {

	private static final long serialVersionUID = 1L;
	private JTextArea descriptionArea;
	private JLabel settingName;
	private JLabel settingDefault;
	private JLabel sinceVersion;
	private JLabel directives;

	public SettingPanel() {
		super();
		initialize();
	}

	public SettingPanel(SettingModel setting) {
		this();
		setSetting(setting);
	}

	private void initialize() {
		descriptionArea = new JTextArea();
		descriptionArea.setRows(6);
		descriptionArea.setFont(new Font("monospaced", Font.PLAIN, 11));
		descriptionArea.setEditable(false);

		settingName = new JLabel(" ");
		settingName.setFont(settingName.getFont().deriveFont(Font.BOLD));

		settingDefault = new JLabel(" ");

		sinceVersion = new JLabel(" ", SwingConstants.RIGHT);
		sinceVersion.setForeground(new Color(0, 0x80, 0));

		directives = new JLabel(" ", SwingConstants.RIGHT);
		directives.setFont(directives.getFont().deriveFont(Font.ITALIC));

		JPanel settingParameters = new JPanel(new GridLayout(2, 2, 0, 0));
		settingParameters.add(settingName);
		settingParameters.add(sinceVersion);
		settingParameters.add(settingDefault, BorderLayout.CENTER);
		settingParameters.add(directives);

		JPanel settingPanel = new JPanel(new BorderLayout(5, 5));
		settingPanel.add(settingParameters, BorderLayout.NORTH);
		settingPanel.add(new JScrollPane(descriptionArea), BorderLayout.CENTER);
		setLayout(new BorderLayout(0, 0));
		add(settingPanel, BorderLayout.CENTER);
	}

	public void setSetting(SettingModel setting) {
		settingName.setText(setting.name);
		if (setting.since == null) {
			sinceVersion.setText("custom");
		} else {
			sinceVersion.setText("since " + setting.since);
		}
		settingDefault.setText(Translation.get("gb.default") + ": " + setting.defaultValue);

		List<String> values = new ArrayList<String>();
		if (setting.caseSensitive) {
			values.add("CASE-SENSITIVE");
		}
		if (setting.spaceDelimited) {
			values.add("SPACE-DELIMITED");
		}
		if (setting.restartRequired) {
			values.add("RESTART REQUIRED");
		}
		directives.setText(StringUtils.flattenStrings(values, ", "));

		descriptionArea.setText(setting.description);
		descriptionArea.setCaretPosition(0);
	}

	public void clear() {
		settingName.setText(" ");
		settingDefault.setText(" ");
		sinceVersion.setText(" ");
		directives.setText(" ");
		descriptionArea.setText("");
	}
}
