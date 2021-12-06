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

import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.ResourceBundle;
import java.util.TimeZone;

/**
 * Utility class of time functions.
 *
 * @author James Moger
 *
 */
public class TimeUtils {
	public static final long MIN = 1000 * 60L;

	public static final long HALFHOUR = MIN * 30L;

	public static final long ONEHOUR = HALFHOUR * 2;

	public static final long ONEDAY = ONEHOUR * 24L;

	public static final long ONEYEAR = ONEDAY * 365L;

	private final ResourceBundle translation;

	private final TimeZone timezone;

	public TimeUtils() {
		this(null, null);
	}

	public TimeUtils(ResourceBundle translation, TimeZone timezone) {
		this.translation = translation;
		this.timezone = timezone;
	}

	/**
	 * Returns true if date is today.
	 *
	 * @param date
	 * @return true if date is today
	 */
	public static boolean isToday(Date date, TimeZone timezone) {
		return isToday(date, timezone, new Date());
	}


	static boolean isToday(Date date, TimeZone timezone, Date now) {
		SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd");
		if (timezone != null) {
			df.setTimeZone(timezone);
		}
		return df.format(now).equals(df.format(date));
	}

	/**
	 * Returns true if date is yesterday.
	 *
	 * @param date
	 * @return true if date is yesterday
	 */
	public static boolean isYesterday(Date date, TimeZone timezone) {
		return isYesterday(date, timezone, new Date());
	}


	static boolean isYesterday(Date date, TimeZone timezone, Date now) {
		Calendar cal = Calendar.getInstance();
		cal.setTime(now);
		cal.add(Calendar.DATE, -1);
		SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd");
		if (timezone != null) {
			df.setTimeZone(timezone);
		}
		return df.format(cal.getTime()).equals(df.format(date));
	}

	/**
	 * Returns the string representation of the duration as days, months and/or
	 * years.
	 *
	 * @param days
	 * @return duration as string in days, months, and/or years
	 */
	public String duration(int days) {
		if (days <= 60) {
			return (days > 1 ? translate(days, "gb.duration.days", "{0} days") : translate("gb.duration.oneDay", "1 day"));
		} else if (days < 365) {
			int rem = days % 30;
			return translate(((days / 30) + (rem >= 15 ? 1 : 0)), "gb.duration.months", "{0} months");
		} else {
			int years = days / 365;
			int rem = days % 365;
			String yearsString = (years > 1 ? translate(years, "gb.duration.years", "{0} years") : translate("gb.duration.oneYear", "1 year"));
			if (rem < 30) {
				if (rem == 0) {
					return yearsString;
				} else {
					return yearsString + (rem >= 15 ? (", " + translate("gb.duration.oneMonth", "1 month")): "");
				}
			} else {
				int months = rem / 30;
				int remDays = rem % 30;
				if (remDays >= 15) {
					months++;
				}
				String monthsString = yearsString + ", "
						+ (months > 1 ? translate(months, "gb.duration.months", "{0} months") : translate("gb.duration.oneMonth", "1 month"));
				return monthsString;
			}
		}
	}

	/**
	 * Returns the number of minutes ago between the start time and the end
	 * time.
	 *
	 * @param date
	 * @param endTime
	 * @param roundup
	 * @return difference in minutes
	 */
	public static int minutesAgo(Date date, long endTime, boolean roundup) {
		long diff = endTime - date.getTime();
		int mins = (int) (diff / MIN);
		if (roundup && (diff % MIN) >= 30) {
			mins++;
		}
		return mins;
	}

	/**
	 * Return the difference in minutes between now and the date.
	 *
	 * @param date
	 * @param roundup
	 * @return minutes ago
	 */
	public static int minutesAgo(Date date, boolean roundup) {
		return minutesAgo(date, System.currentTimeMillis(), roundup);
	}

	/**
	 * Return the difference in hours between now and the date.
	 *
	 * @param date
	 * @param roundup
	 * @return hours ago
	 */
	public static int hoursAgo(Date date, boolean roundup) {
		return hoursAgo(date, System.currentTimeMillis(), roundup);
	}


	static int hoursAgo(Date date, long now, boolean roundup) {
		long diff = now - date.getTime();
		int hours = (int) (diff / ONEHOUR);
		if (roundup && (diff % ONEHOUR) >= HALFHOUR) {
			hours++;
		}
		return hours;
	}

	/**
	 * Return the difference in days between now and the date.
	 *
	 * @param date
	 * @return days ago
	 */
	public static int daysAgo(Date date) {
		return daysAgo(date, System.currentTimeMillis());
	}

	static int daysAgo(Date date, long now) {
		long today = ONEDAY * (now/ONEDAY);
		long day = ONEDAY * (date.getTime()/ONEDAY);
		long diff = today - day;
		int days = (int) (diff / ONEDAY);
		return days;
	}



	/**
	 * Return the difference in calendar days between a given timestamp and the date.
	 * Calendar days means that the difference is calculated between
	 * calendar days, not 24 hour increments.
	 *
	 * This means the result is dependent on the timezone. Only the local
	 * time's time zone is used, i.e. both time stamps are interpreted in
	 * the given time zone.
	 *
	 * E.g. if now is 10:00 on 20.10.2020 GMT and the date given is for
	 * either 6:00 or 20:00 on 18.10.2020 GMT then the result is two days
	 * in both cases.
	 *
	 *
	 * @param date
	 * 			Date in the past
	 * @param now
	 * 			Timestamp representing current time (used for unit tests)
	 * @return calendar days ago
	 */
	static int calendarDaysAgo(Date date, TimeZone timezone, long now) {
		Calendar cal;
		if (timezone == null) {
			cal = Calendar.getInstance();
		} else {
			cal = Calendar.getInstance(timezone);
		}

		cal.setTimeInMillis(now);
		cal.set(Calendar.MILLISECOND, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.HOUR_OF_DAY, 12);
		long today = cal.getTime().getTime();

		cal.clear();
		cal.setTime(date);
		cal.set(Calendar.MILLISECOND, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.HOUR_OF_DAY, 12);
		long day = cal.getTime().getTime();

		long diff = today - day;
		int days = (int) (diff / ONEDAY);
		return days;
	}


	public String today() {
		return translate("gb.time.today", "today");
	}

	public String yesterday() {
		return translate("gb.time.yesterday", "yesterday");
	}

	/**
	 * Returns the string representation of the duration between now and the
	 * date.
	 *
	 * @param date
	 * @return duration as a string
	 */
	public String timeAgo(Date date) {
		return timeAgo(date, false);
	}

	/**
	 * Returns the CSS class for the date based on its age from Now.
	 *
	 * @param date
	 * @return the css class
	 */
	public String timeAgoCss(Date date) {
		return timeAgo(date, true);
	}

	/**
	 * Returns the string representation of the duration OR the css class for
	 * the duration.
	 *
	 * @param date
	 * @param css
	 * @return the string representation of the duration OR the css class
	 */
	private String timeAgo(Date date, boolean css) {
		return timeAgo(date, css, System.currentTimeMillis());
	}


	String timeAgo(Date date, boolean css, long now) {
		Date dNow = new Date(now);
		if (isToday(date, timezone, dNow) || isYesterday(date, timezone, dNow)) {
			int mins = minutesAgo(date, now, true);
			if (mins >= 120) {
				if (css) {
					return "age1";
				}
				int hours = hoursAgo(date, now, true);
				if (hours > 23) {
					return yesterday();
				} else {
					return translate(hours, "gb.time.hoursAgo", "{0} hours ago");
				}
			}
			if (css) {
				return "age0";
			}
			if (mins > 2) {
				return translate(mins, "gb.time.minsAgo", "{0} mins ago");
			}
			return translate("gb.time.justNow", "just now");
		} else {
			int days = calendarDaysAgo(date, timezone, now);
			if (css) {
				if (days <= 7) {
					return "age2";
				} if (days <= 30) {
					return "age3";
				} else {
					return "age4";
				}
			}
			if (days < 365) {
				if (days <= 30) {
					return translate(days, "gb.time.daysAgo", "{0} days ago");
				} else if (days <= 90) {
					int weeks = days / 7;
					if (weeks == 12) {
						return translate(3, "gb.time.monthsAgo", "{0} months ago");
					} else {
						return translate(weeks, "gb.time.weeksAgo", "{0} weeks ago");
					}
				}
				int months = days / 30;
				int weeks = (days % 30) / 7;
				if (weeks >= 2) {
					months++;
				}
				return translate(months, "gb.time.monthsAgo", "{0} months ago");
			} else if (days == 365) {
				return translate("gb.time.oneYearAgo", "1 year ago");
			} else {
				int yr = days / 365;
				days = days % 365;
				int months = (yr * 12) + (days / 30);
				if (months > 23) {
					return translate(yr, "gb.time.yearsAgo", "{0} years ago");
				} else {
					return translate(months, "gb.time.monthsAgo", "{0} months ago");
				}
			}
		}
	}

	public String inFuture(Date date) {
		long diff = date.getTime() - System.currentTimeMillis();
		if (diff > ONEDAY) {
			double days = ((double) diff)/ONEDAY;
			return translate((int) Math.round(days), "gb.time.inDays", "in {0} days");
		} else {
			double hours = ((double) diff)/ONEHOUR;
			if (hours > 2) {
				return translate((int) Math.round(hours), "gb.time.inHours", "in {0} hours");
			} else {
				int mins = (int) (diff/MIN);
				return translate(mins, "gb.time.inMinutes", "in {0} minutes");
			}
		}
	}

	private String translate(String key, String defaultValue) {
		String value = defaultValue;
		if (translation != null && translation.containsKey(key)) {
			String aValue = translation.getString(key);
			if (!StringUtils.isEmpty(aValue)) {
				value = aValue;
			}
		}
		return value;
	}

	private String translate(int val, String key, String defaultPattern) {
		String pattern = defaultPattern;
		if (translation != null && translation.containsKey(key)) {
			String aValue = translation.getString(key);
			if (!StringUtils.isEmpty(aValue)) {
				pattern = aValue;
			}
		}
		return MessageFormat.format(pattern, val);
	}

	/**
	 * Convert a frequency string into minutes.
	 *
	 * @param frequency
	 * @param minimumMins
	 * @return minutes
	 */
	public static int convertFrequencyToMinutes(String frequency, int minimumMins) {
		// parse the frequency
		frequency = frequency.toLowerCase();
		int mins = minimumMins;
		if (!StringUtils.isEmpty(frequency)) {
			try {
				String str = frequency.trim();
				if (frequency.indexOf(' ') > -1) {
					str = str.substring(0, str.indexOf(' ')).trim();
				}
				mins = (int) Float.parseFloat(str);
			} catch (NumberFormatException e) {
			}
			if (mins < minimumMins) {
				mins = minimumMins;
			}
			if (frequency.indexOf("day") > -1) {
				// convert to minutes
				mins *= 1440;
			} else if (frequency.indexOf("hour") > -1) {
				// convert to minutes
				mins *= 60;
			}
		}
		return mins;
	}
}
