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
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Paint;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;

import com.gitblit.utils.StringUtils;

public class HeaderPanel extends JPanel {

	private static final long serialVersionUID = 1L;

	private final Insets insets = new Insets(5, 5, 5, 5);

	private Color lightColor = new Color(0, 0, 0x60);

	private JLabel headerLabel;

	private JLabel refreshLabel;

	public HeaderPanel(String text, String icon) {
		// super(new FlowLayout(FlowLayout.LEFT), true);
		super(new GridLayout(1, 2, 5, 5), true);
		setOpaque(true);
		setBackground(new Color(0, 0, 0x20));

		headerLabel = new JLabel(text);
		if (!StringUtils.isEmpty(icon)) {
			headerLabel.setIcon(new ImageIcon(getClass().getResource("/" + icon)));
		}
		headerLabel.setForeground(Color.white);
		headerLabel.setFont(headerLabel.getFont().deriveFont(14f));
		add(headerLabel);

		refreshLabel = new JLabel("", JLabel.RIGHT);
		refreshLabel.setForeground(Color.white);
		add(refreshLabel);
	}

	public void setText(String text) {
		headerLabel.setText(text);
		SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		refreshLabel.setText("refreshed " + df.format(new Date()));
	}

	@Override
	public Insets getInsets() {
		return insets;
	}

	@Override
	public void paintComponent(Graphics oldG) {
		Graphics2D g = (Graphics2D) oldG;
		Point2D startPoint = new Point2D.Float(0, 0);
		Point2D endPoint = new Point2D.Float(0, getHeight());
		Paint gradientPaint = new GradientPaint(startPoint, lightColor, endPoint, getBackground(),
				false);
		g.setPaint(gradientPaint);
		g.fill(new Rectangle2D.Double(0, 0, getWidth(), getHeight()));
		g.setColor(new Color(0xff, 0x99, 0x00));
		int stroke = 2;
		g.setStroke(new BasicStroke(stroke));
		g.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
	}
}
