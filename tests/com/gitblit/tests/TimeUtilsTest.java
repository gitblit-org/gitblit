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
		assertEquals("1 day", TimeUtils.duration(1));
		assertEquals("5 days", TimeUtils.duration(5));
		assertEquals("3 months", TimeUtils.duration(75));
		assertEquals("12 months", TimeUtils.duration(364));
		assertEquals("1 year", TimeUtils.duration(365 + 0));
		assertEquals("1 year", TimeUtils.duration(365 + 10));
		assertEquals("1 year, 1 month", TimeUtils.duration(365 + 15));
		assertEquals("1 year, 1 month", TimeUtils.duration(365 + 30));
		assertEquals("1 year, 1 month", TimeUtils.duration(365 + 44));
		assertEquals("1 year, 2 months", TimeUtils.duration(365 + 45));
		assertEquals("1 year, 2 months", TimeUtils.duration(365 + 60));

		assertEquals("2 years", TimeUtils.duration(2 * 365 + 0));
		assertEquals("2 years", TimeUtils.duration(2 * 365 + 10));
		assertEquals("2 years, 1 month", TimeUtils.duration(2 * 365 + 15));
		assertEquals("2 years, 1 month", TimeUtils.duration(2 * 365 + 30));
		assertEquals("2 years, 1 month", TimeUtils.duration(2 * 365 + 44));
		assertEquals("2 years, 2 months", TimeUtils.duration(2 * 365 + 45));
		assertEquals("2 years, 2 months", TimeUtils.duration(2 * 365 + 60));
	}

	public void testTimeAgo() throws Exception {
		// standard time ago tests
		assertEquals("1 min ago", TimeUtils.timeAgo(offset(1 * TimeUtils.MIN)));
		assertEquals("60 mins ago", TimeUtils.timeAgo(offset(60 * TimeUtils.MIN)));
		assertEquals("2 hours ago", TimeUtils.timeAgo(offset(120 * TimeUtils.MIN)));
		assertEquals("15 hours ago", TimeUtils.timeAgo(offset(15 * TimeUtils.ONEHOUR)));
		assertEquals("yesterday", TimeUtils.timeAgo(offset(24 * TimeUtils.ONEHOUR)));
		assertEquals("2 days ago", TimeUtils.timeAgo(offset(2 * TimeUtils.ONEDAY)));
		assertEquals("5 weeks ago", TimeUtils.timeAgo(offset(35 * TimeUtils.ONEDAY)));
		assertEquals("3 months ago", TimeUtils.timeAgo(offset(84 * TimeUtils.ONEDAY)));
		assertEquals("3 months ago", TimeUtils.timeAgo(offset(95 * TimeUtils.ONEDAY)));
		assertEquals("4 months ago", TimeUtils.timeAgo(offset(104 * TimeUtils.ONEDAY)));
		assertEquals("1 year ago", TimeUtils.timeAgo(offset(365 * TimeUtils.ONEDAY)));
		assertEquals("13 months ago", TimeUtils.timeAgo(offset(395 * TimeUtils.ONEDAY)));
		assertEquals("2 years ago", TimeUtils.timeAgo(offset((2 * 365 + 30) * TimeUtils.ONEDAY)));

		// css class tests
		assertEquals("age0", TimeUtils.timeAgoCss(offset(1 * TimeUtils.MIN)));
		assertEquals("age0", TimeUtils.timeAgoCss(offset(60 * TimeUtils.MIN)));
		assertEquals("age1", TimeUtils.timeAgoCss(offset(120 * TimeUtils.MIN)));
		assertEquals("age1", TimeUtils.timeAgoCss(offset(24 * TimeUtils.ONEHOUR)));
		assertEquals("age2", TimeUtils.timeAgoCss(offset(2 * TimeUtils.ONEDAY)));
	}

	public void testFrequency() {
		assertEquals(5, TimeUtils.convertFrequencyToMinutes("2 mins"));
		assertEquals(10, TimeUtils.convertFrequencyToMinutes("10 mins"));
		assertEquals(600, TimeUtils.convertFrequencyToMinutes("10 hours"));
		assertEquals(14400, TimeUtils.convertFrequencyToMinutes(" 10 days "));
	}
}
