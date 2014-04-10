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
package com.gitblit.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.parboiled.common.StringUtils;

import ro.fortsoft.pf4j.PluginVersion;

/**
 * Represents a list of plugin registrations.
 */
public class PluginRegistry implements Serializable {

	private static final long serialVersionUID = 1L;

	public final String name;

	public final List<PluginRegistration> registrations;

	public PluginRegistry(String name) {
		this.name = name;
		registrations = new CopyOnWriteArrayList<PluginRegistration>();
	}

	public void setup() {
		for (PluginRegistration reg : registrations) {
			reg.registry = name;
		}
	}

	public PluginRegistration lookup(String idOrName) {
		for (PluginRegistration registration : registrations) {
			if (registration.id.equalsIgnoreCase(idOrName)
					|| registration.name.equalsIgnoreCase(idOrName)) {
				return registration;
			}
		}
		return null;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName();
	}

	public static enum InstallState {
		NOT_INSTALLED, INSTALLED, CAN_UPDATE, UNKNOWN
	}

	/**
	 * Represents a plugin registration.
	 */
	public static class PluginRegistration implements Serializable {

		private static final long serialVersionUID = 1L;

		public final String id;

		public String name;

		public String description;

		public String provider;

		public String projectUrl;

		public String currentRelease;

		public transient String installedRelease;

		public transient String registry;

		public List<PluginRelease> releases;

		public PluginRegistration(String id) {
			this.id = id;
			this.releases = new ArrayList<PluginRelease>();
		}

		public PluginRelease getCurrentRelease() {
			PluginRelease current = null;
			if (!StringUtils.isEmpty(currentRelease)) {
				// find specified
				current = getRelease(currentRelease);
			}

			if (current == null) {
				// find by date
				Date date = new Date(0);
				for (PluginRelease pv : releases) {
					if (pv.date.after(date)) {
						current = pv;
					}
				}
			}
			return current;
		}

		public PluginRelease getRelease(String version) {
			for (PluginRelease pv : releases) {
				if (pv.version.equalsIgnoreCase(version)) {
					return pv;
				}
			}
			return null;
		}

		public InstallState getInstallState() {
			if (StringUtils.isEmpty(installedRelease)) {
				return InstallState.NOT_INSTALLED;
			}
			PluginVersion ir = PluginVersion.createVersion(installedRelease);
			PluginVersion cr = PluginVersion.createVersion(currentRelease);
			switch (ir.compareTo(cr)) {
			case -1:
				return InstallState.UNKNOWN;
			case 1:
				return InstallState.CAN_UPDATE;
			default:
				return InstallState.INSTALLED;
			}
		}

		@Override
		public String toString() {
			return id;
		}
	}

	public static class PluginRelease implements Comparable<PluginRelease> {
		public String version;
		public Date date;
		public String requires;
		public String url;

		@Override
		public int compareTo(PluginRelease o) {
			return PluginVersion.createVersion(version).compareTo(PluginVersion.createVersion(o.version));
		}
	}
}
