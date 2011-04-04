/*
 * Copyright 2011 Squeal Group.  Licensed under the Eclipse Public License, 
 * Version 1.0 (http://www.eclipse.org/legal/epl-v10.html).
 * Initial Developer: Squeal Group
 */
package com.gitblit.utils;

import java.text.DecimalFormat;
import java.text.FieldPosition;
import java.text.Format;
import java.text.ParsePosition;

/**
 * A formatter for formatting byte sizes. For example, formatting 12345 byes
 * results in "12.1 K" and 1234567 results in "1.18 MB".
 * 
 */
public class ByteFormat extends Format {

	private static final long serialVersionUID = 1L;

	public ByteFormat() {
	}

	// Implemented from the Format class

	/**
	 * Formats a long which represent a number of bytes.
	 */
	public String format(long bytes) {
		return format(Long.valueOf(bytes));
	}

	/**
	 * Formats a long which represent a number of kilobytes.
	 */
	public String formatKB(long kilobytes) {
		return format(Long.valueOf(kilobytes * 1024));
	}

	/**
	 * Format the given object (must be a Long).
	 * 
	 * @param obj
	 *            assumed to be the number of bytes as a Long.
	 * @param buf
	 *            the StringBuffer to append to.
	 * @param pos
	 * @return A formatted string representing the given bytes in more
	 *         human-readable form.
	 */
	public StringBuffer format(Object obj, StringBuffer buf, FieldPosition pos) {
		if (obj instanceof Long) {
			long numBytes = ((Long) obj).longValue();
			if (numBytes < 1024) {
				DecimalFormat formatter = new DecimalFormat("#,##0");
				buf.append(formatter.format((double) numBytes)).append(" b");
			} else if (numBytes < 1024 * 1024) {
				DecimalFormat formatter = new DecimalFormat("#,##0.0");
				buf.append(formatter.format((double) numBytes / 1024.0)).append(" KB");
			} else if (numBytes < 1024 * 1024 * 1024) {
				DecimalFormat formatter = new DecimalFormat("#,##0.0");
				buf.append(formatter.format((double) numBytes / (1024.0 * 1024.0))).append(" MB");
			} else {
				DecimalFormat formatter = new DecimalFormat("#,##0.0");
				buf.append(formatter.format((double) numBytes / (1024.0 * 1024.0 * 1024.0))).append(" GB");
			}
		}
		return buf;
	}

	/**
	 * In this implementation, returns null always.
	 * 
	 * @param source
	 * @param pos
	 * @return returns null in this implementation.
	 */
	public Object parseObject(String source, ParsePosition pos) {
		return null;
	}
}
