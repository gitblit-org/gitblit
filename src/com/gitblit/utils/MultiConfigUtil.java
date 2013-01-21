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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Utility class to convert Strings into Collections and vice versa.
 * 
 * @author saheba
 *
 */
public class MultiConfigUtil implements Serializable {
	private static final long serialVersionUID = 1324076956473037856L;

	public static final String OPTION_SEPARATOR = ";";

	/**
	 * converts a collection of strings into a single line string by concatenating them and separating the different elements with the OPTION_SEPARATOR 
	 * 
	 * @param collection of strings
	 * 
	 * @return
	 */
	public String convertCollectionToSingleLineString(Collection<String> collection) {
		String result = "";
		for (String string : collection) {
			if (!result.equals("")) {
				result += OPTION_SEPARATOR;
			}
			result += string;
		}
		return result;
	}
	
	/**
	 * converts a collection of strings into a list of strings 
	 * 
	 * @param collection
	 * 
	 * @return
	 */
	public List<String> convertCollectionToList(Collection<String> collection) {
		List<String> result = new ArrayList<String>();
		for (String string : collection) {
				result.add(string);
		}
		return result;
	}

	/**
	 * converts a single line string into a set of strings by splitting the given string with the OPTION_SEPARATOR 
	 * 
	 * @param string which contains one or more options concatenated with the OPTION_SEPARATOR
	 * 
	 * @return
	 */
	public Set<String> convertStringToSet(String string) {
		Set<String> result = new HashSet<String>();
		if (string != null && string.trim().length() > 0) {
			String[] splitted = string.split(OPTION_SEPARATOR);
			for (int i = 0; i < splitted.length; i++) {
				String possible = splitted[i].trim();
				if (possible.length() > 0) {
					result.add(possible);
				}
			}
		}
		return result;
	}
}
