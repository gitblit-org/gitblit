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
package com.gitblit.wicket.charting;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.gitblit.utils.StringUtils;

/**
 * Abstract parent class for Google Charts built with the Visualization API.
 * 
 * @author James Moger
 * 
 */
public abstract class GoogleChart implements Serializable {

	private static final long serialVersionUID = 1L;
	final String tagId;
	final String dataName;
	final String title;
	final String keyName;
	final String valueName;
	final List<ChartValue> values;
	int width;
	int height;

	public GoogleChart(String tagId, String title, String keyName, String valueName) {
		this.tagId = tagId;
		this.dataName = StringUtils.getSHA1(title).substring(0, 8);
		this.title = title;
		this.keyName = keyName;
		this.valueName = valueName;
		values = new ArrayList<ChartValue>();
	}

	public void setWidth(int width) {
		this.width = width;
	}

	public void setHeight(int height) {
		this.height = height;
	}

	public void addValue(String name, int value) {
		values.add(new ChartValue(name, value));
	}

	public void addValue(String name, float value) {
		values.add(new ChartValue(name, value));
	}

	public void addValue(String name, double value) {
		values.add(new ChartValue(name, (float) value));
	}

	protected abstract void appendChart(StringBuilder sb);

	protected void line(StringBuilder sb, String line) {
		sb.append(line);
		sb.append('\n');
	}

	protected class ChartValue implements Serializable, Comparable<ChartValue> {

		private static final long serialVersionUID = 1L;

		final String name;
		final float value;

		ChartValue(String name, float value) {
			this.name = name;
			this.value = value;
		}

		@Override
		public int compareTo(ChartValue o) {
			// sorts the dataset by largest value first
			if (value > o.value) {
				return -1;
			} else if (value < o.value) {
				return 1;
			}
			return 0;
		}
	}
}
