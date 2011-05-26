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

import java.util.Date;

public class TimeUtils {
	public static final long MIN = 1000 * 60L;

	public static final long HALFHOUR = MIN * 30L;

	public static final long ONEHOUR = HALFHOUR * 2;

	public static final long ONEDAY = ONEHOUR * 24L;

	public static final long ONEYEAR = ONEDAY * 365L;

	@SuppressWarnings("deprecation")
	public static boolean isToday(Date date) {
		Date now = new Date();
		return now.getDate() == date.getDate() && now.getMonth() == date.getMonth()
				&& now.getYear() == date.getYear();
	}

	@SuppressWarnings("deprecation")
	public static boolean isYesterday(Date date) {
		Date now = new Date();
		return now.getDate() == (date.getDate() + 1) && now.getMonth() == date.getMonth()
				&& now.getYear() == date.getYear();
	}

	public static String duration(int days) {
		if (days <= 60) {
			return days + (days > 1 ? " days" : " day");
		} else if (days <= 365) {
			int rem = days % 30;
			return (days / 30) + " months, " + rem + (rem > 1 ? " days" : " day");
		} else {
			int years = days / 365;
			int rem = days % 365;
			String yearsString = years + (years > 1 ? " years" : " year");
			if (rem < 30) {
				if (rem == 0) {
					return yearsString;
				} else {
					return yearsString + ", " + rem + (rem > 1 ? " days" : " day");
				}
			} else {
				int months = rem / 30;
				int remDays = rem % 30;
				String monthsString;
				if (months == 0) {
					monthsString = yearsString;
				} else {
					monthsString = yearsString + ", " + months
							+ (months > 1 ? " months" : " month");
				}
				if (remDays == 0) {
					return monthsString;
				} else {
					return monthsString + ", " + remDays + (remDays > 1 ? " days" : " day");
				}
			}
		}
	}

	public static int minutesAgo(Date date, long endTime, boolean roundup) {
		long diff = endTime - date.getTime();
		int mins = (int) (diff / MIN);
		if (roundup && (diff % MIN) >= 30) {
			mins++;
		}
		return mins;
	}

	public static int minutesAgo(Date date, boolean roundup) {
		return minutesAgo(date, System.currentTimeMillis(), roundup);
	}

	public static int hoursAgo(Date date, boolean roundup) {
		long diff = System.currentTimeMillis() - date.getTime();
		int hours = (int) (diff / ONEHOUR);
		if (roundup && (diff % ONEHOUR) >= HALFHOUR) {
			hours++;
		}
		return hours;
	}

	public static int daysAgo(Date date, boolean roundup) {
		long diff = System.currentTimeMillis() - date.getTime();
		int days = (int) (diff / ONEDAY);
		if (roundup && (diff % ONEDAY) > 0) {
			days++;
		}
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
					if (weeks == 12) {
						ago = "3 months ago";
					} else {
						ago = weeks + " weeks ago";
					}
				} else if (days > 90) {
					int months = days / 30;
					int weeks = (days % 30) / 7;
					if (weeks >= 2) {
						months++;
					}
					ago = months + " month" + (months > 1 ? "s" : "") + " ago";
				} else {
					ago = days + " day" + (days > 1 ? "s" : "") + " ago";
				}
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
}
