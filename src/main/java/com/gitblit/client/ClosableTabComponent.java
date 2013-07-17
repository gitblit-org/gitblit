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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.plaf.basic.BasicButtonUI;

/**
 * Closable tab control.
 */
public class ClosableTabComponent extends JPanel {

	private static final long serialVersionUID = 1L;

	private static final MouseListener BUTTON_MOUSE_LISTENER = new MouseAdapter() {
		public void mouseEntered(MouseEvent e) {
			Component component = e.getComponent();
			if (component instanceof AbstractButton) {
				AbstractButton button = (AbstractButton) component;
				button.setBorderPainted(true);
			}
		}

		public void mouseExited(MouseEvent e) {
			Component component = e.getComponent();
			if (component instanceof AbstractButton) {
				AbstractButton button = (AbstractButton) component;
				button.setBorderPainted(false);
			}
		}
	};

	private final JTabbedPane pane;
	private final JLabel label;
	private final JButton button = new TabButton();

	private final CloseTabListener closeListener;

	public interface CloseTabListener {
		void closeTab(Component c);
	}

	public ClosableTabComponent(String title, ImageIcon icon, JTabbedPane pane,
			CloseTabListener closeListener) {
		super(new FlowLayout(FlowLayout.LEFT, 0, 0));
		this.closeListener = closeListener;

		if (pane == null) {
			throw new NullPointerException("TabbedPane is null");
		}
		this.pane = pane;
		setOpaque(false);
		label = new JLabel(title);
		if (icon != null) {
			label.setIcon(icon);
		}

		add(label);
		label.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 5));
		add(button);
		setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
	}

	private class TabButton extends JButton implements ActionListener {

		private static final long serialVersionUID = 1L;

		public TabButton() {
			int size = 17;
			setPreferredSize(new Dimension(size, size));
			setToolTipText("Close");
			setUI(new BasicButtonUI());
			setContentAreaFilled(false);
			setFocusable(false);
			setBorder(BorderFactory.createEtchedBorder());
			setBorderPainted(false);
			addMouseListener(BUTTON_MOUSE_LISTENER);
			setRolloverEnabled(true);
			addActionListener(this);
		}

		public void actionPerformed(ActionEvent e) {
			int i = pane.indexOfTabComponent(ClosableTabComponent.this);
			Component c = pane.getComponentAt(i);
			if (i != -1) {
				pane.remove(i);
			}
			if (closeListener != null) {
				closeListener.closeTab(c);
			}
		}

		public void updateUI() {
		}

		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			Graphics2D g2 = (Graphics2D) g;
			Stroke stroke = g2.getStroke();
			g2.setStroke(new BasicStroke(2));
			g.setColor(Color.BLACK);
			if (getModel().isRollover()) {
				Color highlight = new Color(0, 51, 153);
				g.setColor(highlight);
			}
			int delta = 5;
			g.drawLine(delta, delta, getWidth() - delta - 1, getHeight() - delta - 1);
			g.drawLine(getWidth() - delta - 1, delta, delta, getHeight() - delta - 1);
			g2.setStroke(stroke);

			int i = pane.indexOfTabComponent(ClosableTabComponent.this);
			pane.setTitleAt(i, label.getText());
		}
	}
}
