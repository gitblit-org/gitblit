/*
 * Copyright 2012 gitblit.com.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;


/**
 * Utility class for arrays and collections.
 *
 * @author James Moger
 *
 */
public class ArrayUtils {

	public static boolean isEmpty(byte [] array) {
		return array == null || array.length == 0;
	}

	public static boolean isEmpty(char [] array) {
		return array == null || array.length == 0;
	}

	public static boolean isEmpty(Object [] array) {
		return array == null || array.length == 0;
	}

	public static boolean isEmpty(Collection<?> collection) {
		return collection == null || collection.isEmpty();
	}

	public static String toString(Collection<?> collection) {
		if (isEmpty(collection)) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		for (Object o : collection) {
			sb.append(o.toString()).append(", ");
		}
		// trim trailing comma-space
		sb.setLength(sb.length() - 2);
		return sb.toString();
	}

	public static Collection<String> fromString(String value) {
		if (StringUtils.isEmpty(value)) {
			value = "";
		}
		List<String> list = new ArrayList<String>();
		String [] values = value.split(",|;");
		for (String v : values) {
			String string = v.trim();
			if (!StringUtils.isEmpty(string)) {
				list.add(string);
			}
		}
		return list;
	}

	public static <X> List<X> join(List<X>... elements) {
		List<X> list = new ArrayList<X>();
		for (List<X> element : elements) {
			list.addAll(element);
		}
		return list;
	}

	public static <X> List<X> join(X[]... elements) {
		List<X> list = new ArrayList<X>();
		for (X[] element : elements) {
			list.addAll(Arrays.asList(element));
		}
		return list;
	}
}
