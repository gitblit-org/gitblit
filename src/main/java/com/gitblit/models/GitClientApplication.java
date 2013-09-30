/*
 * Copyright 2013 gitblit.com.
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

import com.gitblit.Constants.AccessPermission;
import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.StringUtils;

/**
 * Model class to represent a git client application.
 *
 * @author James Moger
 *
 */
public class GitClientApplication implements Serializable {

	private static final long serialVersionUID = 1L;

	public String name;
	public String title;
	public String description;
	public String legal;
	public String icon;
	public String cloneUrl;
	public String command;
	public String productUrl;
	public String [] transports;
	public String[] platforms;
	public AccessPermission minimumPermission;
	public boolean isActive;

	public boolean allowsPlatform(String p) {
		if (ArrayUtils.isEmpty(platforms)) {
			// all platforms
			return true;
		}
		if (StringUtils.isEmpty(p)) {
			return false;
		}
		String plc = p.toLowerCase();
		for (String platform : platforms) {
			if (plc.contains(platform)) {
				return true;
			}
		}
		return false;
	}

	public boolean supportsTransport(String transportOrUrl) {
		if (ArrayUtils.isEmpty(transports)) {
			return true;
		}

		String scheme = transportOrUrl;
		if (transportOrUrl.indexOf(':') > -1) {
			// strip scheme
			scheme = transportOrUrl.substring(0, transportOrUrl.indexOf(':'));
		}

		for (String transport : transports) {
			if (transport.equalsIgnoreCase(scheme)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public String toString() {
		return StringUtils.isEmpty(title) ? name : title;
	}
}