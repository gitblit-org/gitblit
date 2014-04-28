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

import ro.fortsoft.pf4j.Version;

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

	public PluginRegistration lookup(String id) {
		for (PluginRegistration registration : registrations) {
			if (registration.id.equalsIgnoreCase(id)) {
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
		NOT_INSTALLED, INSTALLED, UPDATE_AVAILABLE, UNKNOWN
	}

	/**
	 * Represents a plugin registration.
	 */
	public static class PluginRegistration implements Serializable {

		private static final long serialVersionUID = 1L;

		public final String id;

		public String description;

		public String provider;

		public String projectUrl;

		public transient String installedRelease;

		public transient String registry;

		public List<PluginRelease> releases;

		public PluginRegistration(String id) {
			this.id = id;
			this.releases = new ArrayList<PluginRelease>();
		}

		public PluginRelease getCurrentRelease(Version system) {
			PluginRelease current = null;
			Date date = new Date(0);
			for (PluginRelease pv : releases) {
				Version requires = Version.ZERO;
				if (!StringUtils.isEmpty(pv.requires)) {
					requires = Version.createVersion(pv.requires);
				}

				if (system.isZero() || system.atLeast(requires)) {
					if (pv.date.after(date)) {
						current = pv;
						date = pv.date;
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

		public InstallState getInstallState(Version system) {
			if (StringUtils.isEmpty(installedRelease)) {
				return InstallState.NOT_INSTALLED;
			}
			Version ir = Version.createVersion(installedRelease);
			Version cr = Version.ZERO;
			PluginRelease curr = getCurrentRelease(system);
			if (curr != null) {
				cr = Version.createVersion(curr.version);
			}
			switch (ir.compareTo(cr)) {
			case -1:
				return InstallState.UNKNOWN;
			case 1:
				return InstallState.UPDATE_AVAILABLE;
			default:
				return InstallState.INSTALLED;
			}
		}

		@Override
		public String toString() {
			return id;
		}
	}

	public static class PluginRelease implements Serializable, Comparable<PluginRelease> {

		private static final long serialVersionUID = 1L;

		public String version;
		public Date date;
		public String requires;
		public String url;

		@Override
		public int compareTo(PluginRelease o) {
			return Version.createVersion(version).compareTo(Version.createVersion(o.version));
		}
	}
}
