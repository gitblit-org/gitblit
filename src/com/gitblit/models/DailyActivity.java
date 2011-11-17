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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Model class to represent the commit activity across many repositories. This
 * class is used by the Activity page.
 * 
 * @author James Moger
 */
public class DailyActivity implements Serializable, Comparable<DailyActivity> {

	private static final long serialVersionUID = 1L;

	public final Date date;

	public final List<RepositoryCommit> commits;

	public DailyActivity(Date date) {
		this.date = date;
		commits = new ArrayList<RepositoryCommit>();
	}

	@Override
	public int compareTo(DailyActivity o) {
		// reverse chronological order
		return o.date.compareTo(date);
	}
}
