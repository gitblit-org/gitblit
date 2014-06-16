/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gitblit.wicket;

import java.net.URL;
import java.util.Comparator;

/**
 * A comparator of URL instances.
 *
 * Comparing URLs with their implementation of #equals() is
 * bad because it may cause problems like DNS resolving, or other
 * slow checks. This comparator uses the external form of an URL
 * to make a simple comparison of two Strings.
 *
 * @since 1.5.6
 */
public class UrlExternalFormComparator implements Comparator<URL>
{
	@Override
	public int compare(URL url1, URL url2)
	{
		return url1.toExternalForm().compareTo(url2.toExternalForm());
	}
}