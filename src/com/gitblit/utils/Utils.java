package com.gitblit.utils;

import java.util.Date;

public class Utils {
	private final static long min = 1000 * 60l;

	private final static long halfhour = min * 30l;

	private final static long onehour = halfhour * 2;

	private final static long oneday = onehour * 24l;

	@SuppressWarnings("deprecation")
	public static boolean isToday(Date date) {
		Date now = new Date();
		return now.getDate() == date.getDate() && now.getMonth() == date.getMonth() && now.getYear() == date.getYear();
	}

	@SuppressWarnings("deprecation")
	public static boolean isYesterday(Date date) {
		Date now = new Date();
		return now.getDate() == (date.getDate() + 1) && now.getMonth() == date.getMonth() && now.getYear() == date.getYear();
	}

	public static int minutesAgo(Date date, long endTime, boolean roundup) {
		long diff = endTime - date.getTime();
		int mins = (int) (diff / min);
		if (roundup && (diff % min) >= 30)
			mins++;
		return mins;
	}

	public static int minutesAgo(Date date, boolean roundup) {
		return minutesAgo(date, System.currentTimeMillis(), roundup);
	}

	public static int hoursAgo(Date date, boolean roundup) {
		long diff = System.currentTimeMillis() - date.getTime();
		int hours = (int) (diff / onehour);
		if (roundup && (diff % onehour) >= halfhour)
			hours++;
		return hours;
	}

	public static int daysAgo(Date date, boolean roundup) {
		long diff = System.currentTimeMillis() - date.getTime();
		int days = (int) (diff / oneday);
		if (roundup && (diff % oneday) > 0)
			days++;
		return days;
	}

	public static String timeAgo(Date date) {
		return timeAgo(date, false);
	}

	public static String timeAgoCss(Date date) {
		return timeAgo(date, true);
	}

	private static String timeAgo(Date date, boolean css) {
		String ago = null;
		if (isToday(date) || isYesterday(date)) {
			int mins = minutesAgo(date, true);
			if (mins > 120) {
				if (css) {
					return "age1";
				}
				int hours = hoursAgo(date, true);
				if (hours > 23) {
					ago = "yesterday";
				} else {
					ago = hours + " hour" + (hours > 1 ? "s" : "") + " ago";
				}
			} else {
				if (css) {
					return "age0";
				}
				ago = mins + " min" + (mins > 1 ? "s" : "") + " ago";
			}
		} else {
			if (css) {
				return "age2";
			}
			int days = daysAgo(date, true);
			if (days < 365) {
				if (days <= 30) {
					ago = days + " day" + (days > 1 ? "s" : "") + " ago";
				} else if (days <= 90) {
					int weeks = days / 7;
					if (weeks == 12)
						ago = "3 months ago";
					else
						ago = weeks + " weeks ago";
				} else if (days > 90) {
					int months = days / 30;
					int weeks = (days % 30) / 7;
					if (weeks >= 2)
						months++;
					ago = months + " month" + (months > 1 ? "s" : "") + " ago";
				} else
					ago = days + " day" + (days > 1 ? "s" : "") + " ago";
			} else if (days == 365) {
				ago = "1 year ago";
			} else {
				int yr = days / 365;
				days = days % 365;
				int months = (yr * 12) + (days / 30);
				if (months > 23) {
					ago = yr + " years ago";
				} else {
					ago = months + " months ago";
				}
			}
		}
		return ago;
	}
	
	public static String leftPad(String input, int length, char pad) {
		if (input.length() < length) {
			StringBuilder sb = new StringBuilder();
			for (int i = 0, len = length - input.length(); i < len; i++) {
				sb.append(pad);
			}
			sb.append(input);
			return sb.toString();
		}
		return input;
	}
	
	public static String rightPad(String input, int length, char pad) {
		if (input.length() < length) {
			StringBuilder sb = new StringBuilder();
			sb.append(input);
			for (int i = 0, len = length - input.length(); i < len; i++) {
				sb.append(pad);
			}			
			return sb.toString();
		}
		return input;
	}
	
	public static String escapeForHtml(String inStr, boolean changeSpace) {
		StringBuffer retStr = new StringBuffer();
		int i = 0;
		while (i < inStr.length()) {
			if (inStr.charAt(i) == '&') {
				retStr.append("&amp;");
			} else if (inStr.charAt(i) == '<') {
				retStr.append("&lt;");
			} else if (inStr.charAt(i) == '>') {
				retStr.append("&gt;");
			} else if (inStr.charAt(i) == '\"') {
				retStr.append("&quot;");
			} else if (changeSpace && inStr.charAt(i) == ' ') {
				retStr.append("&nbsp;");
			} else if (changeSpace && inStr.charAt(i) == '\t') {
				retStr.append(" &nbsp; &nbsp;");
			} else
				retStr.append(inStr.charAt(i));
			i++;
		}
		return retStr.toString();
	}
}
