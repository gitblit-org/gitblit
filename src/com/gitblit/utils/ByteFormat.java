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

import java.text.DecimalFormat;
import java.text.FieldPosition;
import java.text.Format;
import java.text.ParsePosition;

/**
 * ByteFormat is a formatter which takes numbers and returns filesizes in bytes,
 * kilobytes, megabytes, or gigabytes.
 * 
 * @author James Moger
 * 
 */
public class ByteFormat extends Format {

	private static final long serialVersionUID = 1L;

	public ByteFormat() {
	}

	public String format(long value) {
		return format(Long.valueOf(value));
	}

	public StringBuffer format(Object obj, StringBuffer buf, FieldPosition pos) {
		if (obj instanceof Number) {
			long numBytes = ((Number) obj).longValue();
			if (numBytes < 1024) {
				DecimalFormat formatter = new DecimalFormat("#,##0");
				buf.append(formatter.format((double) numBytes)).append(" b");
			} else if (numBytes < 1024 * 1024) {
				DecimalFormat formatter = new DecimalFormat("#,##0");
				buf.append(formatter.format((double) numBytes / 1024.0)).append(" KB");
			} else if (numBytes < 1024 * 1024 * 1024) {
				DecimalFormat formatter = new DecimalFormat("#,##0.0");
				buf.append(formatter.format((double) numBytes / (1024.0 * 1024.0))).append(" MB");
			} else {
				DecimalFormat formatter = new DecimalFormat("#,##0.0");
				buf.append(formatter.format((double) numBytes / (1024.0 * 1024.0 * 1024.0)))
						.append(" GB");
			}
		}
		return buf;
	}

	public Object parseObject(String source, ParsePosition pos) {
		return null;
	}
}
