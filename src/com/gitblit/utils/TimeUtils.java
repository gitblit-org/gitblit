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

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

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

	/**
	 * Returns true if date is today.
	 * 
	 * @param date
	 * @return true if date is today
	 */
	public static boolean isToday(Date date) {
		return (System.currentTimeMillis() - date.getTime()) < ONEDAY;
	}

	/**
	 * Returns true if date is yesterday.
	 * 
	 * @param date
	 * @return true if date is yesterday
	 */
	public static boolean isYesterday(Date date) {
		Calendar cal = Calendar.getInstance();
		cal.setTime(new Date());
		cal.add(Calendar.DATE, -1);
		SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd");
		return df.format(cal.getTime()).equals(df.format(date));
	}

	/**
	 * Returns the string representation of the duration as days, months and/or
	 * years.
	 * 
	 * @param days
	 * @return duration as string in days, months, and/or years
	 */
	public static String duration(int days) {
		if (days <= 60) {
			return days + (days > 1 ? " days" : " day");
		} else if (days < 365) {
			int rem = days % 30;
			return ((days / 30) + (rem >= 15 ? 1 : 0)) + " months";
		} else {
			int years = days / 365;
			int rem = days % 365;
			String yearsString = years + (years > 1 ? " years" : " year");
			if (rem < 30) {
				if (rem == 0) {
					return yearsString;
				} else {
					return yearsString + (rem >= 15 ? ", 1 month" : "");
				}
			} else {
				int months = rem / 30;
				int remDays = rem % 30;
				if (remDays >= 15) {
					months++;
				}
				String monthsString = yearsString + ", " + months
						+ (months > 1 ? " months" : " month");
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
		long diff = System.currentTimeMillis() - date.getTime();
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
		long today = ONEDAY * (System.currentTimeMillis()/ONEDAY);
		long day = ONEDAY * (date.getTime()/ONEDAY);
		long diff = today - day;
		int days = (int) (diff / ONEDAY);
		return days;
	}

	/**
	 * Returns the string representation of the duration between now and the
	 * date.
	 * 
	 * @param date
	 * @return duration as a string
	 */
	public static String timeAgo(Date date) {
		return timeAgo(date, false);
	}

	/**
	 * Returns the CSS class for the date based on its age from Now.
	 * 
	 * @param date
	 * @return the css class
	 */
	public static String timeAgoCss(Date date) {
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
	private static String timeAgo(Date date, boolean css) {
		if (isToday(date) || isYesterday(date)) {
			int mins = minutesAgo(date, true);
			if (mins >= 120) {
				if (css) {
					return "age1";
				}
				int hours = hoursAgo(date, true);
				if (hours > 23) {
					return "yesterday";
				} else {
					return hours + " hours ago";
				}
			}
			if (css) {
				return "age0";
			}
			return mins + " min" + (mins > 1 ? "s" : "") + " ago";
		} else {
			if (css) {
				return "age2";
			}
			int days = daysAgo(date);
			if (days < 365) {
				if (days <= 30) {
					return days + " days ago";
				} else if (days <= 90) {
					int weeks = days / 7;
					if (weeks == 12) {
						return "3 months ago";
					} else {
						return weeks + " weeks ago";
					}
				}
				int months = days / 30;
				int weeks = (days % 30) / 7;
				if (weeks >= 2) {
					months++;
				}
				return months + " months ago";
			} else if (days == 365) {
				return "1 year ago";
			} else {
				int yr = days / 365;
				days = days % 365;
				int months = (yr * 12) + (days / 30);
				if (months > 23) {
					return yr + " years ago";
				} else {
					return months + " months ago";
				}
			}
		}
	}

	/**
	 * Convert a frequency string into minutes.
	 * 
	 * @param frequency
	 * @return minutes
	 */
	public static int convertFrequencyToMinutes(String frequency) {
		// parse the frequency
		frequency = frequency.toLowerCase();
		int mins = 60;
		if (!StringUtils.isEmpty(frequency)) {
			try {
				String str = frequency.trim();
				if (frequency.indexOf(' ') > -1) {
					str = str.substring(0, str.indexOf(' ')).trim();
				}
				mins = (int) Float.parseFloat(str);
			} catch (NumberFormatException e) {
			}
			if (mins < 5) {
				mins = 5;
			}
		}
		if (frequency.indexOf("day") > -1) {
			// convert to minutes
			mins *= 1440;
		} else if (frequency.indexOf("hour") > -1) {
			// convert to minutes
			mins *= 60;
		} else if (frequency.indexOf("min") > -1) {
			// default mins
		}
		return mins;
	}
}
