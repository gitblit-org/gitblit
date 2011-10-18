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

import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

import javax.swing.JLabel;
import javax.swing.JPanel;

public class HeaderPanel extends JPanel {

	private static final long serialVersionUID = 1L;

	private Color lightColor = new Color(0, 0, 0x60);

	public HeaderPanel(String text) {
		super(new FlowLayout(FlowLayout.LEFT), true);
		setOpaque(true);
		setBackground(new Color(0, 0, 0x20));

		JLabel label = new JLabel(text);
		label.setForeground(Color.white);
		label.setFont(label.getFont().deriveFont(14f));
		add(label);
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
	}
}
