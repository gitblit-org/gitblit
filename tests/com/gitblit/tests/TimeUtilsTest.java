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
package com.gitblit.tests;

import java.util.Date;

import junit.framework.TestCase;

import com.gitblit.utils.TimeUtils;

public class TimeUtilsTest extends TestCase {

	private Date offset(long subtract) {
		return new Date(System.currentTimeMillis() - subtract);
	}

	public void testBasicTimeFunctions() throws Exception {
		assertTrue(TimeUtils.minutesAgo(offset(2 * TimeUtils.MIN), false) == 2);
		assertTrue(TimeUtils.minutesAgo(offset((2 * TimeUtils.MIN) + (35 * 1000L)), true) == 3);

		assertTrue(TimeUtils.hoursAgo(offset(2 * TimeUtils.ONEHOUR), false) == 2);
		assertTrue(TimeUtils.hoursAgo(offset(5 * TimeUtils.HALFHOUR), true) == 3);

		assertTrue(TimeUtils.daysAgo(offset(4 * TimeUtils.ONEDAY), false) == 4);
		assertTrue(TimeUtils.daysAgo(offset(4 * TimeUtils.ONEDAY + 12 * TimeUtils.ONEHOUR), true) == 5);
	}

	public void testToday() throws Exception {
		assertTrue(TimeUtils.isToday(new Date()));
	}

	public void testYesterday() throws Exception {
		assertTrue(TimeUtils.isYesterday(offset(TimeUtils.ONEDAY)));
	}

	public void testDurations() throws Exception {
		assertTrue(TimeUtils.duration(1).equals("1 day"));
		assertTrue(TimeUtils.duration(5).equals("5 days"));
		assertTrue(TimeUtils.duration(75).equals("3 months"));
		assertTrue(TimeUtils.duration(364).equals("12 months"));
		assertTrue(TimeUtils.duration(365 + 0).equals("1 year"));
		assertTrue(TimeUtils.duration(365 + 10).equals("1 year"));
		assertTrue(TimeUtils.duration(365 + 15).equals("1 year, 1 month"));
		assertTrue(TimeUtils.duration(365 + 30).equals("1 year, 1 month"));
		assertTrue(TimeUtils.duration(365 + 44).equals("1 year, 1 month"));
		assertTrue(TimeUtils.duration(365 + 45).equals("1 year, 2 months"));
		assertTrue(TimeUtils.duration(365 + 60).equals("1 year, 2 months"));

		assertTrue(TimeUtils.duration(2 * 365 + 0).equals("2 years"));
		assertTrue(TimeUtils.duration(2 * 365 + 10).equals("2 years"));
		assertTrue(TimeUtils.duration(2 * 365 + 15).equals("2 years, 1 month"));
		assertTrue(TimeUtils.duration(2 * 365 + 30).equals("2 years, 1 month"));
		assertTrue(TimeUtils.duration(2 * 365 + 44).equals("2 years, 1 month"));
		assertTrue(TimeUtils.duration(2 * 365 + 45).equals("2 years, 2 months"));
		assertTrue(TimeUtils.duration(2 * 365 + 60).equals("2 years, 2 months"));
	}

	public void testTimeAgo() throws Exception {
		// standard time ago tests
		assertTrue(TimeUtils.timeAgo(offset(1 * TimeUtils.MIN)).equals("1 min ago"));
		assertTrue(TimeUtils.timeAgo(offset(60 * TimeUtils.MIN)).equals("60 mins ago"));
		assertTrue(TimeUtils.timeAgo(offset(120 * TimeUtils.MIN)).equals("2 hours ago"));
		assertTrue(TimeUtils.timeAgo(offset(15 * TimeUtils.ONEHOUR)).equals("15 hours ago"));
		assertTrue(TimeUtils.timeAgo(offset(24 * TimeUtils.ONEHOUR)).equals("yesterday"));
		assertTrue(TimeUtils.timeAgo(offset(2 * TimeUtils.ONEDAY)).equals("2 days ago"));
		assertTrue(TimeUtils.timeAgo(offset(35 * TimeUtils.ONEDAY)).equals("5 weeks ago"));
		assertTrue(TimeUtils.timeAgo(offset(84 * TimeUtils.ONEDAY)).equals("3 months ago"));
		assertTrue(TimeUtils.timeAgo(offset(95 * TimeUtils.ONEDAY)).equals("3 months ago"));
		assertTrue(TimeUtils.timeAgo(offset(104 * TimeUtils.ONEDAY)).equals("4 months ago"));
		assertTrue(TimeUtils.timeAgo(offset(365 * TimeUtils.ONEDAY)).equals("1 year ago"));
		assertTrue(TimeUtils.timeAgo(offset(395 * TimeUtils.ONEDAY)).equals("13 months ago"));
		assertTrue(TimeUtils.timeAgo(offset((2 * 365 + 30) * TimeUtils.ONEDAY)).equals(
				"2 years ago"));

		// css class tests
		assertTrue(TimeUtils.timeAgoCss(offset(1 * TimeUtils.MIN)).equals("age0"));
		assertTrue(TimeUtils.timeAgoCss(offset(60 * TimeUtils.MIN)).equals("age0"));
		assertTrue(TimeUtils.timeAgoCss(offset(120 * TimeUtils.MIN)).equals("age1"));
		assertTrue(TimeUtils.timeAgoCss(offset(24 * TimeUtils.ONEHOUR)).equals("age1"));
		assertTrue(TimeUtils.timeAgoCss(offset(2 * TimeUtils.ONEDAY)).equals("age2"));
	}
}
