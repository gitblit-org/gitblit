/*
 * Copyright 2012 gitblit.com.
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
package com.gitblit.authority;

import java.awt.Component;

import javax.swing.ImageIcon;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;

import com.gitblit.client.Translation;

/**
 * Displays a subscribed icon on the left of the repository name, if there is at
 * least one subscribed branch.
 *
 * @author James Moger
 *
 */
public class CertificateStatusRenderer extends DefaultTableCellRenderer {

	private static final long serialVersionUID = 1L;

	private final ImageIcon unknownIcon;
	private final ImageIcon revokedIcon;
	private final ImageIcon expiredIcon;
	private final ImageIcon expiringIcon;
	private final ImageIcon okIcon;

	public CertificateStatusRenderer() {
		super();
		unknownIcon = new ImageIcon(getClass().getResource("/bullet_white.png"));
		revokedIcon = new ImageIcon(getClass().getResource("/bullet_delete.png"));
		expiredIcon = new ImageIcon(getClass().getResource("/bullet_red.png"));
		expiringIcon = new ImageIcon(getClass().getResource("/bullet_orange.png"));
		okIcon = new ImageIcon(getClass().getResource("/bullet_green.png"));
	}

	@Override
	public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
			boolean hasFocus, int row, int column) {
		super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
		if (value instanceof CertificateStatus) {
			CertificateStatus status = (CertificateStatus) value;
			switch(status) {
				case revoked:
					setText(Translation.get("gb.revoked"));
					setIcon(revokedIcon);
					break;
				case expiring:
					setText(Translation.get("gb.expiring"));
					setIcon(expiringIcon);
					break;
				case expired:
					setText(Translation.get("gb.expired"));
					setIcon(expiredIcon);
					break;
				case unknown:
					setText("");
					setIcon(unknownIcon);
					break;
				default:
					setText(Translation.get("gb.ok"));
					setIcon(okIcon);
					break;
			}
		}
		return this;
	}
}
