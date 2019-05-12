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

import static org.junit.Assert.*;

import java.util.Date;

import org.junit.Test;

import com.gitblit.utils.TimeUtils;

public class TimeUtilsTest {

	private Date offset(long subtract) {
		return new Date(System.currentTimeMillis() - subtract);
	}

	@Test
	public void testBasicTimeFunctions() throws Exception {
		assertEquals(2, TimeUtils.minutesAgo(offset(2 * TimeUtils.MIN), false));
		assertEquals(3, TimeUtils.minutesAgo(offset((2 * TimeUtils.MIN) + (35 * 1000L)), true));

		assertEquals(2, TimeUtils.hoursAgo(offset(2 * TimeUtils.ONEHOUR), false));
		assertEquals(3, TimeUtils.hoursAgo(offset(5 * TimeUtils.HALFHOUR), true));

		assertEquals(4, TimeUtils.daysAgo(offset(4 * TimeUtils.ONEDAY)));
	}

	@Test
	public void testToday() throws Exception {
		assertTrue(TimeUtils.isToday(new Date(), null));
	}

	@Test
	public void testYesterday() throws Exception {
		assertTrue(TimeUtils.isYesterday(offset(TimeUtils.ONEDAY), null));
	}

	@Test
	public void testDurations() throws Exception {
		TimeUtils timeUtils = new TimeUtils();
		assertEquals("1 day", timeUtils.duration(1));
		assertEquals("5 days", timeUtils.duration(5));
		assertEquals("3 months", timeUtils.duration(75));
		assertEquals("12 months", timeUtils.duration(364));
		assertEquals("1 year", timeUtils.duration(365 + 0));
		assertEquals("1 year", timeUtils.duration(365 + 10));
		assertEquals("1 year, 1 month", timeUtils.duration(365 + 15));
		assertEquals("1 year, 1 month", timeUtils.duration(365 + 30));
		assertEquals("1 year, 1 month", timeUtils.duration(365 + 44));
		assertEquals("1 year, 2 months", timeUtils.duration(365 + 45));
		assertEquals("1 year, 2 months", timeUtils.duration(365 + 60));

		assertEquals("2 years", timeUtils.duration(2 * 365 + 0));
		assertEquals("2 years", timeUtils.duration(2 * 365 + 10));
		assertEquals("2 years, 1 month", timeUtils.duration(2 * 365 + 15));
		assertEquals("2 years, 1 month", timeUtils.duration(2 * 365 + 30));
		assertEquals("2 years, 1 month", timeUtils.duration(2 * 365 + 44));
		assertEquals("2 years, 2 months", timeUtils.duration(2 * 365 + 45));
		assertEquals("2 years, 2 months", timeUtils.duration(2 * 365 + 60));
	}

	@Test
	public void testTimeAgo() throws Exception {
		// standard time ago tests
		TimeUtils timeUtils = new TimeUtils();
		assertEquals("just now", timeUtils.timeAgo(offset(1 * TimeUtils.MIN)));
		assertEquals("60 mins ago", timeUtils.timeAgo(offset(60 * TimeUtils.MIN)));
		assertEquals("2 hours ago", timeUtils.timeAgo(offset(120 * TimeUtils.MIN)));
		assertEquals("15 hours ago", timeUtils.timeAgo(offset(15 * TimeUtils.ONEHOUR)));
		assertEquals("yesterday", timeUtils.timeAgo(offset(24 * TimeUtils.ONEHOUR)));
		assertEquals("2 days ago", timeUtils.timeAgo(offset(2 * TimeUtils.ONEDAY)));
		assertEquals("5 weeks ago", timeUtils.timeAgo(offset(35 * TimeUtils.ONEDAY)));
		assertEquals("3 months ago", timeUtils.timeAgo(offset(84 * TimeUtils.ONEDAY)));
		assertEquals("3 months ago", timeUtils.timeAgo(offset(95 * TimeUtils.ONEDAY)));
		assertEquals("4 months ago", timeUtils.timeAgo(offset(104 * TimeUtils.ONEDAY)));
		assertEquals("1 year ago", timeUtils.timeAgo(offset(365 * TimeUtils.ONEDAY)));
		assertEquals("13 months ago", timeUtils.timeAgo(offset(395 * TimeUtils.ONEDAY)));
		assertEquals("2 years ago", timeUtils.timeAgo(offset((2 * 365 + 30) * TimeUtils.ONEDAY)));

		// css class tests
		assertEquals("age0", timeUtils.timeAgoCss(offset(1 * TimeUtils.MIN)));
		assertEquals("age0", timeUtils.timeAgoCss(offset(60 * TimeUtils.MIN)));
		assertEquals("age1", timeUtils.timeAgoCss(offset(120 * TimeUtils.MIN)));
		assertEquals("age1", timeUtils.timeAgoCss(offset(24 * TimeUtils.ONEHOUR)));
		assertEquals("age2", timeUtils.timeAgoCss(offset(2 * TimeUtils.ONEDAY)));
	}

	@Test
	public void testFrequency() {
		assertEquals(5, TimeUtils.convertFrequencyToMinutes("2 mins", 5));
		assertEquals(10, TimeUtils.convertFrequencyToMinutes("10 mins", 5));
		assertEquals(600, TimeUtils.convertFrequencyToMinutes("10 hours", 5));
		assertEquals(14400, TimeUtils.convertFrequencyToMinutes(" 10 days ", 5));
	}
}
