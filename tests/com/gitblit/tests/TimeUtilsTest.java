package com.gitblit.tests;

import java.util.Date;

import junit.framework.TestCase;

import com.gitblit.utils.TimeUtils;

public class TimeUtilsTest extends TestCase {

	public void testToday() throws Exception {
		assertTrue("Is today failed!", TimeUtils.isToday(new Date()));
	}

	public void testYesterday() throws Exception {
		assertTrue("Is yesterday failed!", TimeUtils.isYesterday(new Date(System.currentTimeMillis() - TimeUtils.ONEDAY)));
	}

	public void testDurations() throws Exception {
		assertTrue(TimeUtils.duration(5).equals("5 days"));
		assertTrue(TimeUtils.duration(364).equals("12 months"));
		assertTrue(TimeUtils.duration(365 + 0).equals("1 year"));
		assertTrue(TimeUtils.duration(365 + 10).equals("1 year"));
		assertTrue(TimeUtils.duration(365 + 15).equals("1 year, 1 month"));
		assertTrue(TimeUtils.duration(365 + 30).equals("1 year, 1 month"));
		assertTrue(TimeUtils.duration(365 + 44).equals("1 year, 1 month"));
		assertTrue(TimeUtils.duration(365 + 45).equals("1 year, 2 months"));
		assertTrue(TimeUtils.duration(365 + 60).equals("1 year, 2 months"));
		
		assertTrue(TimeUtils.duration(2*365 + 0).equals("2 years"));
		assertTrue(TimeUtils.duration(2*365 + 10).equals("2 years"));
		assertTrue(TimeUtils.duration(2*365 + 15).equals("2 years, 1 month"));
		assertTrue(TimeUtils.duration(2*365 + 30).equals("2 years, 1 month"));
		assertTrue(TimeUtils.duration(2*365 + 44).equals("2 years, 1 month"));
		assertTrue(TimeUtils.duration(2*365 + 45).equals("2 years, 2 months"));
		assertTrue(TimeUtils.duration(2*365 + 60).equals("2 years, 2 months"));
	}
	
	public void testTimeAgo() throws Exception {
		long time = System.currentTimeMillis();
		assertTrue(TimeUtils.timeAgo(new Date(time - 1*TimeUtils.MIN)).equals("1 min ago"));
		assertTrue(TimeUtils.timeAgo(new Date(time - 60*TimeUtils.MIN)).equals("60 mins ago"));
		assertTrue(TimeUtils.timeAgo(new Date(time - 120*TimeUtils.MIN)).equals("2 hours ago"));
		assertTrue(TimeUtils.timeAgo(new Date(time - 15*TimeUtils.ONEHOUR)).equals("15 hours ago"));
		assertTrue(TimeUtils.timeAgo(new Date(time - 24*TimeUtils.ONEHOUR)).equals("yesterday"));
		assertTrue(TimeUtils.timeAgo(new Date(time - 2*TimeUtils.ONEDAY)).equals("2 days ago"));
		assertTrue(TimeUtils.timeAgo(new Date(time - 35*TimeUtils.ONEDAY)).equals("5 weeks ago"));
		assertTrue(TimeUtils.timeAgo(new Date(time - 84*TimeUtils.ONEDAY)).equals("3 months ago"));
		assertTrue(TimeUtils.timeAgo(new Date(time - 95*TimeUtils.ONEDAY)).equals("3 months ago"));
		assertTrue(TimeUtils.timeAgo(new Date(time - 104*TimeUtils.ONEDAY)).equals("4 months ago"));
		assertTrue(TimeUtils.timeAgo(new Date(time - 365*TimeUtils.ONEDAY)).equals("1 year ago"));
		assertTrue(TimeUtils.timeAgo(new Date(time - 395*TimeUtils.ONEDAY)).equals("13 months ago"));
		assertTrue(TimeUtils.timeAgo(new Date(time - (2*365 + 30)*TimeUtils.ONEDAY)).equals("2 years ago"));
		
		assertTrue(TimeUtils.timeAgoCss(new Date(time - 1*TimeUtils.MIN)).equals("age0"));
		assertTrue(TimeUtils.timeAgoCss(new Date(time - 60*TimeUtils.MIN)).equals("age0"));
		assertTrue(TimeUtils.timeAgoCss(new Date(time - 120*TimeUtils.MIN)).equals("age1"));
		assertTrue(TimeUtils.timeAgoCss(new Date(time - 24*TimeUtils.ONEHOUR)).equals("age1"));
		assertTrue(TimeUtils.timeAgoCss(new Date(time - 2*TimeUtils.ONEDAY)).equals("age2"));	}
}
