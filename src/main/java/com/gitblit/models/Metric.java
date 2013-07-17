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
package com.gitblit.models;

import java.io.Serializable;

/**
 * Metric is a serializable model class that encapsulates metrics for some given
 * type.
 * 
 * @author James Moger
 * 
 */
public class Metric implements Serializable, Comparable<Metric> {

	private static final long serialVersionUID = 1L;

	public final String name;
	public double count;
	public double tag;
	public int duration;

	public Metric(String name) {
		this.name = name;
	}

	@Override
	public int compareTo(Metric o) {
		if (count > o.count) {
			return -1;
		}
		if (count < o.count) {
			return 1;
		}
		return 0;
	}
}