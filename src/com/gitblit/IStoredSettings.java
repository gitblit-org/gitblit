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
package com.gitblit;

import java.util.List;

public interface IStoredSettings {

	List<String> getAllKeys(String startingWith);

	boolean getBoolean(String name, boolean defaultValue);

	int getInteger(String name, int defaultValue);

	String getString(String name, String defaultValue);

	List<String> getStrings(String name);

	List<String> getStringsFromValue(String value);

	List<String> getStrings(String name, String separator);

	List<String> getStringsFromValue(String value, String separator);

}