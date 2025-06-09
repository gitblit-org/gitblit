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
package com.gitblit.models;

import java.io.Serializable;
import java.util.Date;
import java.util.Map;
import java.util.TreeMap;

import com.gitblit.Constants;

/**
 * ServerStatus encapsulates runtime status information about the server
 * including some information about the system environment.
 *
 * @author James Moger
 *
 */
public class ServerStatus implements Serializable {

	private static final long serialVersionUID = 1L;

	public final Date bootDate;

	public final String version;

	public final String releaseDate;

	public final Map<String, String> systemProperties;

	public final long heapMaximum;

	public volatile long heapAllocated;

	public volatile long heapFree;

	public boolean isGO;

	public String servletContainer;

	public ServerStatus(String version) {
		this.bootDate = new Date();
		this.version = version;
		this.releaseDate = Constants.getBuildDate();

		this.heapMaximum = Runtime.getRuntime().maxMemory();

		this.systemProperties = new TreeMap<String, String>();
		put("file.encoding");
		put("java.home");
		put("java.awt.headless");
		put("java.io.tmpdir");
		put("java.runtime.name");
		put("java.runtime.version");
		put("java.vendor");
		put("java.version");
		put("java.vm.info");
		put("java.vm.name");
		put("java.vm.vendor");
		put("java.vm.version");
		put("os.arch");
		put("os.name");
		put("os.version");
	}

	public ServerStatus() {
		this(Constants.getVersion());
	}

	private void put(String key) {
		systemProperties.put(key, System.getProperty(key));
	}
}
