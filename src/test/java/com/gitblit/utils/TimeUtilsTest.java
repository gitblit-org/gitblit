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
package com.gitblit.utils;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import com.gitblit.tests.GitblitUnitTest;
import org.junit.Test;


public class TimeUtilsTest extends GitblitUnitTest
{

	private Date offset(long subtract) {
		return new Date(System.currentTimeMillis() - subtract);
	}

	private Date offset(long now, long subtract) {
		return new Date(now - subtract);
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
	}

	@Test
	public void testTimeAgoCss() throws Exception {
		// css class tests
		TimeUtils timeUtils = new TimeUtils();
		assertEquals("age0", timeUtils.timeAgoCss(offset(1 * TimeUtils.MIN)));
		assertEquals("age0", timeUtils.timeAgoCss(offset(60 * TimeUtils.MIN)));
		assertEquals("age1", timeUtils.timeAgoCss(offset(120 * TimeUtils.MIN)));
		assertEquals("age1", timeUtils.timeAgoCss(offset(24 * TimeUtils.ONEHOUR)));
		assertEquals("age2", timeUtils.timeAgoCss(offset(2 * TimeUtils.ONEDAY)));
	}


	@Test
	public void testTimeAgoYesterday() throws Exception {
		TimeZone myTimezone = TimeZone.getTimeZone("GMT");
		TimeUtils timeUtils = new TimeUtils(null, myTimezone);

		Calendar myCal = Calendar.getInstance(myTimezone);
		myCal.set(Calendar.HOUR_OF_DAY, 12);
		myCal.set(Calendar.MINUTE, 0);
		long now = myCal.getTime().getTime();

		assertNotEquals("yesterday", timeUtils.timeAgo(offset(now,23 * TimeUtils.ONEHOUR), false, now));
		assertNotEquals("yesterday", timeUtils.timeAgo(offset(now,(23 * TimeUtils.ONEHOUR) + (29 * TimeUtils.MIN)), false, now));

		assertEquals("yesterday", timeUtils.timeAgo(offset(now,(23 * TimeUtils.ONEHOUR) + (31 * TimeUtils.MIN)), false, now));
		assertEquals("yesterday", timeUtils.timeAgo(offset(now,24 * TimeUtils.ONEHOUR), false, now));
		assertEquals("yesterday", timeUtils.timeAgo(offset(now,35 * TimeUtils.ONEHOUR), false, now));
		assertEquals("yesterday", timeUtils.timeAgo(offset(now,36 * TimeUtils.ONEHOUR), false, now));

		assertNotEquals("yesterday", timeUtils.timeAgo(offset(now,37 * TimeUtils.ONEHOUR), false, now));
		assertNotEquals("yesterday", timeUtils.timeAgo(offset(now,44 * TimeUtils.ONEHOUR), false, now));
	}

	@Test
	public void testTimeAgoYesterdayCET() throws Exception {
		TimeZone myTimezone = TimeZone.getTimeZone("CET");
		TimeUtils timeUtils = new TimeUtils(null, myTimezone);

		Calendar myCal = Calendar.getInstance(myTimezone);
		myCal.set(Calendar.HOUR_OF_DAY, 22);
		myCal.set(Calendar.MINUTE, 0);
		long now = myCal.getTime().getTime();

		assertNotEquals("yesterday", timeUtils.timeAgo(offset(now,23 * TimeUtils.ONEHOUR), false, now));
		assertNotEquals("yesterday", timeUtils.timeAgo(offset(now,(23 * TimeUtils.ONEHOUR) + (29 * TimeUtils.MIN)), false, now));

		assertEquals("yesterday", timeUtils.timeAgo(offset(now,(23 * TimeUtils.ONEHOUR) + (31 * TimeUtils.MIN)), false, now));
		assertEquals("yesterday", timeUtils.timeAgo(offset(now,24 * TimeUtils.ONEHOUR), false, now));
		assertEquals("yesterday", timeUtils.timeAgo(offset(now,36 * TimeUtils.ONEHOUR), false, now));
		assertEquals("yesterday", timeUtils.timeAgo(offset(now,46 * TimeUtils.ONEHOUR), false, now));

		assertNotEquals("yesterday", timeUtils.timeAgo(offset(now,47 * TimeUtils.ONEHOUR), false, now));
		assertNotEquals("yesterday", timeUtils.timeAgo(offset(now,56 * TimeUtils.ONEHOUR), false, now));
	}


	@Test
	public void testTimeAgoYesterdayPST() throws Exception {
		TimeZone myTimezone = TimeZone.getTimeZone("PST");
		TimeUtils timeUtils = new TimeUtils(null, myTimezone);

		Calendar myCal = Calendar.getInstance(myTimezone);
		myCal.set(Calendar.HOUR_OF_DAY, 8);
		myCal.set(Calendar.MINUTE, 0);
		long now = myCal.getTime().getTime();

		assertNotEquals("yesterday", timeUtils.timeAgo(offset(now,23 * TimeUtils.ONEHOUR), false, now));
		assertNotEquals("yesterday", timeUtils.timeAgo(offset(now,(23 * TimeUtils.ONEHOUR) + (29 * TimeUtils.MIN)), false, now));

		assertEquals("yesterday", timeUtils.timeAgo(offset(now,(23 * TimeUtils.ONEHOUR) + (31 * TimeUtils.MIN)), false, now));
		assertEquals("yesterday", timeUtils.timeAgo(offset(now,24 * TimeUtils.ONEHOUR), false, now));
		assertEquals("yesterday", timeUtils.timeAgo(offset(now,30 * TimeUtils.ONEHOUR), false, now));
		assertEquals("yesterday", timeUtils.timeAgo(offset(now,32 * TimeUtils.ONEHOUR), false, now));

		assertNotEquals("yesterday", timeUtils.timeAgo(offset(now,33 * TimeUtils.ONEHOUR), false, now));
		assertNotEquals("yesterday", timeUtils.timeAgo(offset(now,48 * TimeUtils.ONEHOUR), false, now));
	}

	@Test
	public void testFrequency() {
		assertEquals(5, TimeUtils.convertFrequencyToMinutes("2 mins", 5));
		assertEquals(10, TimeUtils.convertFrequencyToMinutes("10 mins", 5));
		assertEquals(600, TimeUtils.convertFrequencyToMinutes("10 hours", 5));
		assertEquals(14400, TimeUtils.convertFrequencyToMinutes(" 10 days ", 5));
	}


	@Test
	public void testTimeAgoDaysAgo() throws Exception {
		TimeZone myTimezone = TimeZone.getTimeZone("GMT");
		TimeUtils timeUtils = new TimeUtils(null, myTimezone);

		Calendar myCal = Calendar.getInstance(myTimezone);
		myCal.set(Calendar.HOUR_OF_DAY, 12);
		myCal.set(Calendar.MINUTE, 0);
		long now = myCal.getTime().getTime();

		assertEquals("yesterday", timeUtils.timeAgo(offset(now,24 * TimeUtils.ONEHOUR), false, now));
		assertEquals("yesterday", timeUtils.timeAgo(offset(now,36 * TimeUtils.ONEHOUR), false, now));

		assertEquals("2 days ago", timeUtils.timeAgo(offset(now,37 * TimeUtils.ONEHOUR), false, now));
		assertEquals("2 days ago", timeUtils.timeAgo(offset(now,48 * TimeUtils.ONEHOUR), false, now));
		assertEquals("2 days ago", timeUtils.timeAgo(offset(now,60 * TimeUtils.ONEHOUR), false, now));

		assertEquals("3 days ago", timeUtils.timeAgo(offset(now,61 * TimeUtils.ONEHOUR), false, now));
		assertEquals("3 days ago", timeUtils.timeAgo(offset(now,72 * TimeUtils.ONEHOUR), false, now));
		assertEquals("3 days ago", timeUtils.timeAgo(offset(now,84 * TimeUtils.ONEHOUR), false, now));

 		assertEquals("4 days ago", timeUtils.timeAgo(offset(now,85 * TimeUtils.ONEHOUR), false, now));
	}



	@Test
	public void testTimeAgoDaysAgoCET() throws Exception {
		TimeZone myTimezone = TimeZone.getTimeZone("CET");
		TimeUtils timeUtils = new TimeUtils(null, myTimezone);

		Calendar myCal = Calendar.getInstance(myTimezone);
		myCal.set(Calendar.HOUR_OF_DAY, 8);
		myCal.set(Calendar.MINUTE, 0);
		long now = myCal.getTime().getTime();


		assertEquals("yesterday", timeUtils.timeAgo(offset(now,24 * TimeUtils.ONEHOUR), false, now));
		assertEquals("yesterday", timeUtils.timeAgo(offset(now,32 * TimeUtils.ONEHOUR), false, now));

		assertEquals("2 days ago", timeUtils.timeAgo(offset(now,33 * TimeUtils.ONEHOUR), false, now));
		assertEquals("2 days ago", timeUtils.timeAgo(offset(now,48 * TimeUtils.ONEHOUR), false, now));
		assertEquals("2 days ago", timeUtils.timeAgo(offset(now,56 * TimeUtils.ONEHOUR), false, now));

		assertEquals("3 days ago", timeUtils.timeAgo(offset(now,57 * TimeUtils.ONEHOUR), false, now));
		assertEquals("3 days ago", timeUtils.timeAgo(offset(now,72 * TimeUtils.ONEHOUR), false, now));
		assertEquals("3 days ago", timeUtils.timeAgo(offset(now,80 * TimeUtils.ONEHOUR), false, now));

		assertEquals("4 days ago", timeUtils.timeAgo(offset(now,81 * TimeUtils.ONEHOUR), false, now));
	}



	@Test
	public void testTimeAgoDaysAgoPST() throws Exception {
		TimeZone myTimezone = TimeZone.getTimeZone("PST");
		TimeUtils timeUtils = new TimeUtils(null, myTimezone);

		Calendar myCal = Calendar.getInstance(myTimezone);
		myCal.set(Calendar.HOUR_OF_DAY, 22);
		myCal.set(Calendar.MINUTE, 0);
		long now = myCal.getTime().getTime();

		assertEquals("yesterday", timeUtils.timeAgo(offset(now,24 * TimeUtils.ONEHOUR), false, now));
		assertEquals("yesterday", timeUtils.timeAgo(offset(now,46 * TimeUtils.ONEHOUR), false, now));

		assertEquals("2 days ago", timeUtils.timeAgo(offset(now,47 * TimeUtils.ONEHOUR), false, now));
		assertEquals("2 days ago", timeUtils.timeAgo(offset(now,48 * TimeUtils.ONEHOUR), false, now));
		assertEquals("2 days ago", timeUtils.timeAgo(offset(now,70 * TimeUtils.ONEHOUR), false, now));

		assertEquals("3 days ago", timeUtils.timeAgo(offset(now,71 * TimeUtils.ONEHOUR), false, now));
		assertEquals("3 days ago", timeUtils.timeAgo(offset(now,72 * TimeUtils.ONEHOUR), false, now));
		assertEquals("3 days ago", timeUtils.timeAgo(offset(now,94 * TimeUtils.ONEHOUR), false, now));

		assertEquals("4 days ago", timeUtils.timeAgo(offset(now,95 * TimeUtils.ONEHOUR), false, now));
	}



	/*
	 * Test if time difference is correctly calculated in full calendar days relative to GMT.
	 */
	@Test
	public void testCalendarDaysAgoToGmt() {
		TimeZone myTimezone = TimeZone.getTimeZone("GMT");

		Calendar myCal = Calendar.getInstance(myTimezone);
		myCal.set(2021, Calendar.AUGUST, 19, 12, 0, 5);
		long now = myCal.getTime().getTime();
		// Date from the same time zone
		assertEquals(0, TimeUtils.calendarDaysAgo(offset(now, 11 * TimeUtils.ONEHOUR ), myTimezone, now));
		assertEquals(0, TimeUtils.calendarDaysAgo(offset(now, 12 * TimeUtils.ONEHOUR ), myTimezone, now));

		assertEquals(1, TimeUtils.calendarDaysAgo(offset(now, 12 * TimeUtils.ONEHOUR + 1 * TimeUtils.MIN), myTimezone, now));
		assertEquals(1, TimeUtils.calendarDaysAgo(offset(now, 24 * TimeUtils.ONEHOUR ), myTimezone, now));
		assertEquals(1, TimeUtils.calendarDaysAgo(offset(now, 36 * TimeUtils.ONEHOUR ), myTimezone, now));

		assertEquals(2, TimeUtils.calendarDaysAgo(offset(now,  36 * TimeUtils.ONEHOUR + 10 * 1000), myTimezone, now));
		assertEquals(2, TimeUtils.calendarDaysAgo(offset(now,  48 * TimeUtils.ONEHOUR ), myTimezone, now));
		assertEquals(2, TimeUtils.calendarDaysAgo(offset(now,  60 * TimeUtils.ONEHOUR ), myTimezone, now));

		assertEquals(3, TimeUtils.calendarDaysAgo(offset(now,  61 * TimeUtils.ONEHOUR ), myTimezone, now));


		// What if we get passed a date created from a UTC timestamp that came from a different time zone?
		// CET in August is +2 hours from GMT (CEST). So the day border is shifted two hours forward

		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("CET"));

		cal.set(2021, Calendar.AUGUST, 19, 8, 0, 5);
		Date date = cal.getTime();
		assertEquals(0, TimeUtils.calendarDaysAgo(date, myTimezone, now));

		cal.set(2021, Calendar.AUGUST, 19, 2, 0, 5);
		date = cal.getTime();
		assertEquals(0, TimeUtils.calendarDaysAgo(date, myTimezone, now));


		cal.set(2021, Calendar.AUGUST, 19, 1, 30, 5);
		date = cal.getTime();
		assertEquals(1, TimeUtils.calendarDaysAgo(date, myTimezone, now));

		cal.set(2021, Calendar.AUGUST, 18, 12, 0, 5);
		date = cal.getTime();
		assertEquals(1, TimeUtils.calendarDaysAgo(date, myTimezone, now));

		cal.set(2021, Calendar.AUGUST, 18, 2, 0, 5);
		date = cal.getTime();
		assertEquals(1, TimeUtils.calendarDaysAgo(date, myTimezone, now));


		cal.set(2021, Calendar.AUGUST, 18, 0, 0, 5);
		date = cal.getTime();
		assertEquals(2, TimeUtils.calendarDaysAgo(date, myTimezone, now));

		cal.set(2021, Calendar.AUGUST, 17, 23, 0, 5);
		date = cal.getTime();
		assertEquals(2, TimeUtils.calendarDaysAgo(date, myTimezone, now));

		cal.set(2021, Calendar.AUGUST, 17, 3, 0, 5);
		date = cal.getTime();
		assertEquals(2, TimeUtils.calendarDaysAgo(date, myTimezone, now));


		cal.set(2021, Calendar.AUGUST, 17, 1, 0, 5);
		date = cal.getTime();
		assertEquals(3, TimeUtils.calendarDaysAgo(date, myTimezone, now));


		// Now we travel westwards.
		// PST in August is -7 hours from GMT (PDT). So the day border is shifted seven hours back

		cal = Calendar.getInstance(TimeZone.getTimeZone("PST"));
		cal.clear();

		cal.set(2021, Calendar.AUGUST, 19, 5, 0, 0);
		date = cal.getTime();
		assertEquals(0, TimeUtils.calendarDaysAgo(date, myTimezone, now));

		cal.set(2021, Calendar.AUGUST, 19, 0, 0, 5);
		date = cal.getTime();
		assertEquals(0, TimeUtils.calendarDaysAgo(date, myTimezone, now));

		cal.set(2021, Calendar.AUGUST, 18, 17, 0, 5);
		date = cal.getTime();
		assertEquals(0, TimeUtils.calendarDaysAgo(date, myTimezone, now));


		cal.set(2021, Calendar.AUGUST, 18, 16, 55, 5);
		date = cal.getTime();
		assertEquals(1, TimeUtils.calendarDaysAgo(date, myTimezone, now));

		cal.set(2021, Calendar.AUGUST, 18, 12, 0, 5);
		date = cal.getTime();
		assertEquals(1, TimeUtils.calendarDaysAgo(date, myTimezone, now));

		cal.set(2021, Calendar.AUGUST, 17, 17, 0, 5);
		date = cal.getTime();
		assertEquals(1, TimeUtils.calendarDaysAgo(date, myTimezone, now));


		cal.set(2021, Calendar.AUGUST, 17, 16, 0, 5);
		date = cal.getTime();
		assertEquals(2, TimeUtils.calendarDaysAgo(date, myTimezone, now));

		cal.set(2021, Calendar.AUGUST, 17, 1, 0, 5);
		date = cal.getTime();
		assertEquals(2, TimeUtils.calendarDaysAgo(date, myTimezone, now));

		cal.set(2021, Calendar.AUGUST, 16, 17, 0, 5);
		date = cal.getTime();
		assertEquals(2, TimeUtils.calendarDaysAgo(date, myTimezone, now));


		cal.set(2021, Calendar.AUGUST, 16, 14, 0, 5);
		date = cal.getTime();
		assertEquals(3, TimeUtils.calendarDaysAgo(date, myTimezone, now));
	}


	/*
	 * Test if time difference is correctly calculated in full calendar days relative to CET.
	 */
	@Test
	public void testCalendarDaysAgoToCet() {
		TimeZone myTimezone = TimeZone.getTimeZone("CET");

		Calendar myCal = Calendar.getInstance(myTimezone);
		myCal.set(2020, Calendar.JUNE, 5, 12, 0, 5);
		long now = myCal.getTime().getTime();

		// Date from the same time zone
		assertEquals(0, TimeUtils.calendarDaysAgo(offset(now, 11 * TimeUtils.ONEHOUR ), myTimezone, now));
		assertEquals(0, TimeUtils.calendarDaysAgo(offset(now, 12 * TimeUtils.ONEHOUR ), myTimezone, now));

		assertEquals(1, TimeUtils.calendarDaysAgo(offset(now, 12 * TimeUtils.ONEHOUR + 1 * TimeUtils.MIN), myTimezone, now));
		assertEquals(1, TimeUtils.calendarDaysAgo(offset(now, 24 * TimeUtils.ONEHOUR ), myTimezone, now));
		assertEquals(1, TimeUtils.calendarDaysAgo(offset(now, 36 * TimeUtils.ONEHOUR ), myTimezone, now));

		assertEquals(2, TimeUtils.calendarDaysAgo(offset(now, 36 * TimeUtils.ONEHOUR + 10 * 1000), myTimezone, now));
		assertEquals(2, TimeUtils.calendarDaysAgo(offset(now, 48 * TimeUtils.ONEHOUR ), myTimezone, now));
		assertEquals(2, TimeUtils.calendarDaysAgo(offset(now, 60 * TimeUtils.ONEHOUR ), myTimezone, now));

		assertEquals(3, TimeUtils.calendarDaysAgo(offset(now, 61 * TimeUtils.ONEHOUR ), myTimezone, now));


		// What if we get passed a date created from a UTC timestamp that came from a different time zone?
		// IST in June is +3:30 hours from CEST. So the day border is shifted three and a half hours forward

		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("IST"));

		cal.set(2020, Calendar.JUNE, 5, 13, 0, 5);
		Date date = cal.getTime();
		assertEquals(0, TimeUtils.calendarDaysAgo(date, myTimezone, now));

		cal.set(2020, Calendar.JUNE, 5, 3, 30, 5);
		date = cal.getTime();
		assertEquals(0, TimeUtils.calendarDaysAgo(date, myTimezone, now));


		cal.set(2020, Calendar.JUNE, 5, 3, 29, 5);
		date = cal.getTime();
		assertEquals(1, TimeUtils.calendarDaysAgo(date, myTimezone, now));

		cal.set(2020, Calendar.JUNE, 5, 0, 0, 0);
		date = cal.getTime();
		assertEquals(1, TimeUtils.calendarDaysAgo(date, myTimezone, now));

		cal.set(2020, Calendar.JUNE, 4, 10, 0, 0);
		date = cal.getTime();
		assertEquals(1, TimeUtils.calendarDaysAgo(date, myTimezone, now));

		cal.set(2020, Calendar.JUNE, 4, 4, 0, 5);
		date = cal.getTime();
		assertEquals(1, TimeUtils.calendarDaysAgo(date, myTimezone, now));


		cal.set(2020, Calendar.JUNE, 4, 3, 0, 5);
		date = cal.getTime();
		assertEquals(2, TimeUtils.calendarDaysAgo(date, myTimezone, now));

		cal.set(2020, Calendar.JUNE, 3, 12, 0, 5);
		date = cal.getTime();
		assertEquals(2, TimeUtils.calendarDaysAgo(date, myTimezone, now));

		cal.set(2020, Calendar.JUNE, 3, 4, 0, 5);
		date = cal.getTime();
		assertEquals(2, TimeUtils.calendarDaysAgo(date, myTimezone, now));


		cal.set(2020, Calendar.JUNE, 3, 3, 20, 5);
		date = cal.getTime();
		assertEquals(3, TimeUtils.calendarDaysAgo(date, myTimezone, now));


		// Now we travel westwards to New York.
		// EST in June is -6 hours from CEST (EDT). So the day border is shifted six hours back

		cal = Calendar.getInstance(TimeZone.getTimeZone("EST5EDT"));
		cal.clear();

		cal.set(2020, Calendar.JUNE, 5, 5, 0, 5);
		date = cal.getTime();
		assertEquals(0, TimeUtils.calendarDaysAgo(date, myTimezone, now));

		cal.set(2020, Calendar.JUNE, 5, 0, 0, 5);
		date = cal.getTime();
		assertEquals(0, TimeUtils.calendarDaysAgo(date, myTimezone, now));

		cal.set(2020, Calendar.JUNE, 4, 18, 0, 5);
		date = cal.getTime();
		assertEquals(0, TimeUtils.calendarDaysAgo(date, myTimezone, now));


		cal.set(2020, Calendar.JUNE, 4, 17, 59, 5);
		date = cal.getTime();
		assertEquals(1, TimeUtils.calendarDaysAgo(date, myTimezone, now));

		cal.set(2020, Calendar.JUNE, 4, 12, 0, 5);
		date = cal.getTime();
		assertEquals(1, TimeUtils.calendarDaysAgo(date, myTimezone, now));

		cal.set(2020, Calendar.JUNE, 3, 19, 0, 5);
		date = cal.getTime();
		assertEquals(1, TimeUtils.calendarDaysAgo(date, myTimezone, now));


		cal.set(2020, Calendar.JUNE, 3, 17, 0, 5);
		date = cal.getTime();
		assertEquals(2, TimeUtils.calendarDaysAgo(date, myTimezone, now));

		cal.set(2020, Calendar.JUNE, 3, 8, 0, 5);
		date = cal.getTime();
		assertEquals(2, TimeUtils.calendarDaysAgo(date, myTimezone, now));

		cal.set(2020, Calendar.JUNE, 2, 18, 0, 5);
		date = cal.getTime();
		assertEquals(2, TimeUtils.calendarDaysAgo(date, myTimezone, now));


		cal.set(2020, Calendar.JUNE, 2, 17, 20, 5);
		date = cal.getTime();
		assertEquals(3, TimeUtils.calendarDaysAgo(date, myTimezone, now));
	}


	/*
	 * Test if time difference is correctly calculated in full calendar days relative to AET (Australia).
	 */
	@Test
	public void testCalendarDaysAgoToAet() {
		TimeZone myTimezone = TimeZone.getTimeZone("AET");

		Calendar myCal = Calendar.getInstance(myTimezone);
		myCal.set(2022, Calendar.FEBRUARY, 22, 12, 0, 5);
		long now = myCal.getTime().getTime();

		// Date from the same time zone
		assertEquals(0, TimeUtils.calendarDaysAgo(offset(now, 11 * TimeUtils.ONEHOUR ), myTimezone, now));
		assertEquals(0, TimeUtils.calendarDaysAgo(offset(now,  12 * TimeUtils.ONEHOUR ), myTimezone, now));

		assertEquals(1, TimeUtils.calendarDaysAgo(offset(now,  12 * TimeUtils.ONEHOUR + 1 * TimeUtils.MIN), myTimezone, now));
		assertEquals(1, TimeUtils.calendarDaysAgo(offset(now,  24 * TimeUtils.ONEHOUR ), myTimezone, now));
		assertEquals(1, TimeUtils.calendarDaysAgo(offset(now,  36 * TimeUtils.ONEHOUR ), myTimezone, now));

		assertEquals(2, TimeUtils.calendarDaysAgo(offset(now,  36 * TimeUtils.ONEHOUR + 10 * 1000), myTimezone, now));
		assertEquals(2, TimeUtils.calendarDaysAgo(offset(now,  48 * TimeUtils.ONEHOUR ), myTimezone, now));
		assertEquals(2, TimeUtils.calendarDaysAgo(offset(now,  60 * TimeUtils.ONEHOUR ), myTimezone, now));

		assertEquals(3, TimeUtils.calendarDaysAgo(offset(now,  61 * TimeUtils.ONEHOUR ), myTimezone, now));


		// What if we get passed a date created from a UTC timestamp that came from a different time zone?
		// NZ in February is +2 hours from AEDT. So the day border is shifted two hours forward

		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("NZ"));

		cal.set(2022, Calendar.FEBRUARY, 22, 12, 0, 5);
		Date date = cal.getTime();
		assertEquals(0, TimeUtils.calendarDaysAgo(date, myTimezone, now));

		cal.set(2022, Calendar.FEBRUARY, 22, 2, 0, 5);
		date = cal.getTime();
		assertEquals(0, TimeUtils.calendarDaysAgo(date, myTimezone, now));


		cal.set(2022, Calendar.FEBRUARY, 22, 1, 45, 5);
		date = cal.getTime();
		assertEquals(1, TimeUtils.calendarDaysAgo(date, myTimezone, now));

		cal.set(2022, Calendar.FEBRUARY, 21, 22, 0, 5);
		date = cal.getTime();
		assertEquals(1, TimeUtils.calendarDaysAgo(date, myTimezone, now));

		cal.set(2022, Calendar.FEBRUARY, 21, 12, 0, 5);
		date = cal.getTime();
		assertEquals(1, TimeUtils.calendarDaysAgo(date, myTimezone, now));

		cal.set(2022, Calendar.FEBRUARY, 21, 2, 0, 5);
		date = cal.getTime();
		assertEquals(1, TimeUtils.calendarDaysAgo(date, myTimezone, now));


		cal.set(2022, Calendar.FEBRUARY, 21, 1, 0, 5);
		date = cal.getTime();
		assertEquals(2, TimeUtils.calendarDaysAgo(date, myTimezone, now));

		cal.set(2022, Calendar.FEBRUARY, 20, 10, 0, 5);
		date = cal.getTime();
		assertEquals(2, TimeUtils.calendarDaysAgo(date, myTimezone, now));

		cal.set(2022, Calendar.FEBRUARY, 20, 2, 0, 5);
		date = cal.getTime();
		assertEquals(2, TimeUtils.calendarDaysAgo(date, myTimezone, now));


		cal.set(2022, Calendar.FEBRUARY, 20, 1, 0, 5);
		date = cal.getTime();
		assertEquals(3, TimeUtils.calendarDaysAgo(date, myTimezone, now));


		// Now we travel westwards to Europe.
		// CET in February is -10 hours from AEDT. So the day border is shifted ten hours back

		cal = Calendar.getInstance(TimeZone.getTimeZone("CET"));
		cal.clear();

		cal.set(2022, Calendar.FEBRUARY, 22, 2, 0, 5);
		date = cal.getTime();
		assertEquals(0, TimeUtils.calendarDaysAgo(date, myTimezone, now));

		cal.set(2022, Calendar.FEBRUARY, 22, 0, 0, 5);
		date = cal.getTime();
		assertEquals(0, TimeUtils.calendarDaysAgo(date, myTimezone, now));

		cal.set(2022, Calendar.FEBRUARY, 21, 14, 0, 5);
		date = cal.getTime();
		assertEquals(0, TimeUtils.calendarDaysAgo(date, myTimezone, now));


		cal.set(2022, Calendar.FEBRUARY, 21, 13, 30, 5);
		date = cal.getTime();
		assertEquals(1, TimeUtils.calendarDaysAgo(date, myTimezone, now));

		cal.set(2022, Calendar.FEBRUARY, 21, 7, 0, 5);
		date = cal.getTime();
		assertEquals(1, TimeUtils.calendarDaysAgo(date, myTimezone, now));

		cal.set(2022, Calendar.FEBRUARY, 20, 15, 0, 5);
		date = cal.getTime();
		assertEquals(1, TimeUtils.calendarDaysAgo(date, myTimezone, now));


		cal.set(2022, Calendar.FEBRUARY, 20, 13, 59, 5);
		date = cal.getTime();
		assertEquals(2, TimeUtils.calendarDaysAgo(date, myTimezone, now));

		cal.set(2022, Calendar.FEBRUARY, 20, 1, 0, 5);
		date = cal.getTime();
		assertEquals(2, TimeUtils.calendarDaysAgo(date, myTimezone, now));

		cal.set(2022, Calendar.FEBRUARY, 19, 14, 0, 5);
		date = cal.getTime();
		assertEquals(2, TimeUtils.calendarDaysAgo(date, myTimezone, now));


		cal.set(2022, Calendar.FEBRUARY, 19, 9, 0, 5);
		date = cal.getTime();
		assertEquals(3, TimeUtils.calendarDaysAgo(date, myTimezone, now));


		// Lets continue even further west.
		// AST in February is -15 hours from AEDT. So the day border is shifted fifteen hours back

		cal = Calendar.getInstance(TimeZone.getTimeZone("America/Curacao"));
		cal.clear();

		cal.set(2022, Calendar.FEBRUARY, 21, 21, 0, 0);
		date = cal.getTime();
		assertEquals(0, TimeUtils.calendarDaysAgo(date, myTimezone, now));

		cal.set(2022, Calendar.FEBRUARY, 21, 12, 0, 5);
		date = cal.getTime();
		assertEquals(0, TimeUtils.calendarDaysAgo(date, myTimezone, now));

		cal.set(2022, Calendar.FEBRUARY, 21, 9, 0, 5);
		date = cal.getTime();
		assertEquals(0, TimeUtils.calendarDaysAgo(date, myTimezone, now));


		cal.set(2022, Calendar.FEBRUARY, 21, 8, 0, 5);
		date = cal.getTime();
		assertEquals(1, TimeUtils.calendarDaysAgo(date, myTimezone, now));

		cal.set(2022, Calendar.FEBRUARY, 20, 19, 0, 5);
		date = cal.getTime();
		assertEquals(1, TimeUtils.calendarDaysAgo(date, myTimezone, now));

		cal.set(2022, Calendar.FEBRUARY, 20, 10, 0, 5);
		date = cal.getTime();
		assertEquals(1, TimeUtils.calendarDaysAgo(date, myTimezone, now));


		cal.set(2022, Calendar.FEBRUARY, 20, 8, 50, 5);
		date = cal.getTime();
		assertEquals(2, TimeUtils.calendarDaysAgo(date, myTimezone, now));

		cal.set(2022, Calendar.FEBRUARY, 19, 17, 0, 5);
		date = cal.getTime();
		assertEquals(2, TimeUtils.calendarDaysAgo(date, myTimezone, now));

		cal.set(2022, Calendar.FEBRUARY, 19, 9, 0, 5);
		date = cal.getTime();
		assertEquals(2, TimeUtils.calendarDaysAgo(date, myTimezone, now));


		cal.set(2022, Calendar.FEBRUARY, 19, 7, 0, 5);
		date = cal.getTime();
		assertEquals(3, TimeUtils.calendarDaysAgo(date, myTimezone, now));
	}

}
