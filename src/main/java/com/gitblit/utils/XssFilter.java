/*
 * Copyright 2014 gitblit.com.
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

/**
 * Defines the contract for an XSS filter implementation.
 *
 * @author James Moger
 *
 */
public interface XssFilter {

	/**
	 * Returns a filtered version of the input value that contains no html
	 * elements.
	 *
	 * @param input
	 * @return a plain text value
	 */
	String none(String input);

	/**
	 * Returns a filtered version of the input that contains structural html
	 * elements.
	 *
	 * @param input
	 * @return a filtered html value
	 */
	String relaxed(String input);

	/**
	 * A NOOP XSS filter.
	 *
	 * @author James Moger
	 *
	 */
	public class AllowXssFilter implements XssFilter {

		@Override
		public String none(String input) {
			return input;
		}

		@Override
		public String relaxed(String input) {
			return input;
		}

	}

}
