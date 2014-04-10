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
package com.gitblit.transport.ssh.commands;

import java.util.Collections;
import java.util.List;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import ro.fortsoft.pf4j.ExtensionPoint;
import ro.fortsoft.pf4j.PluginDependency;
import ro.fortsoft.pf4j.PluginDescriptor;
import ro.fortsoft.pf4j.PluginState;
import ro.fortsoft.pf4j.PluginWrapper;

import com.gitblit.manager.IGitblit;
import com.gitblit.models.PluginRegistry.InstallState;
import com.gitblit.models.PluginRegistry.PluginRegistration;
import com.gitblit.models.PluginRegistry.PluginRelease;
import com.gitblit.models.UserModel;
import com.gitblit.utils.FlipTable;
import com.gitblit.utils.FlipTable.Borders;
import com.google.common.base.Joiner;

/**
 * The plugin dispatcher and commands for runtime plugin management.
 *
 * @author James Moger
 *
 */
@CommandMetaData(name = "plugin", description = "Plugin management commands", admin = true)
public class PluginDispatcher extends DispatchCommand {

	@Override
	protected void setup(UserModel user) {
		register(user, ListPlugins.class);
		register(user, StartPlugin.class);
		register(user, StopPlugin.class);
		register(user, EnablePlugin.class);
		register(user, DisablePlugin.class);
		register(user, ShowPlugin.class);
		register(user, RefreshPlugins.class);
		register(user, AvailablePlugins.class);
		register(user, InstallPlugin.class);
		register(user, UninstallPlugin.class);
	}

	@CommandMetaData(name = "list", aliases = { "ls" }, description = "List plugins")
	public static class ListPlugins extends ListCommand<PluginWrapper> {

		@Override
		protected List<PluginWrapper> getItems() throws UnloggedFailure {
			IGitblit gitblit = getContext().getGitblit();
			List<PluginWrapper> list = gitblit.getPlugins();
			return list;
		}

		@Override
		protected void asTable(List<PluginWrapper> list) {
			String[] headers;
			if (verbose) {
				String [] h = { "#", "Id", "Version", "State", "Path", "Provider"};
				headers = h;
			} else {
				String [] h = { "#", "Id", "Version", "State", "Path"};
				headers = h;
			}
			Object[][] data = new Object[list.size()][];
			for (int i = 0; i < list.size(); i++) {
				PluginWrapper p = list.get(i);
				PluginDescriptor d = p.getDescriptor();
				if (verbose) {
					data[i] = new Object[] { "" + (i + 1), d.getPluginId(), d.getVersion(), p.getPluginState(), p.getPluginPath(), d.getProvider() };
				} else {
					data[i] = new Object[] { "" + (i + 1), d.getPluginId(), d.getVersion(), p.getPluginState(), p.getPluginPath() };
				}
			}

			stdout.println(FlipTable.of(headers, data, Borders.BODY_HCOLS));
		}

		@Override
		protected void asTabbed(List<PluginWrapper> list) {
			for (PluginWrapper pw : list) {
				PluginDescriptor d = pw.getDescriptor();
				if (verbose) {
					outTabbed(d.getPluginId(), d.getVersion(), pw.getPluginState(), pw.getPluginPath(), d.getProvider());
				} else {
					outTabbed(d.getPluginId(), d.getVersion(), pw.getPluginState(), pw.getPluginPath());
				}
			}
		}
	}

	static abstract class PluginCommand extends SshCommand {

		protected PluginWrapper getPlugin(String id) throws Failure {
			IGitblit gitblit = getContext().getGitblit();
			PluginWrapper pluginWrapper = null;
			try {
				int index = Integer.parseInt(id);
				List<PluginWrapper> plugins = gitblit.getPlugins();
				if (index > plugins.size()) {
					throw new UnloggedFailure(1, "Invalid plugin index specified!");
				}
				pluginWrapper = plugins.get(index - 1);
			} catch (NumberFormatException e) {
				pluginWrapper = gitblit.getPlugin(id);
				if (pluginWrapper == null) {
					PluginRegistration reg = gitblit.lookupPlugin(id);
					if (reg == null) {
						throw new UnloggedFailure("Invalid plugin specified!");
					}
					pluginWrapper = gitblit.getPlugin(reg.id);
				}
			}

			return pluginWrapper;
		}
	}

	@CommandMetaData(name = "start", description = "Start a plugin")
	public static class StartPlugin extends PluginCommand {

		@Argument(index = 0, required = true, metaVar = "ALL|<id>", usage = "the plugin to start")
		protected String id;

		@Override
		public void run() throws Failure {
			IGitblit gitblit = getContext().getGitblit();
			if (id.equalsIgnoreCase("ALL")) {
				gitblit.startPlugins();
				stdout.println("All plugins started");
			} else {
				PluginWrapper pluginWrapper = getPlugin(id);
				if (pluginWrapper == null) {
					throw new UnloggedFailure(String.format("Plugin %s is not installed!", id));
				}

				PluginState state = gitblit.startPlugin(pluginWrapper.getPluginId());
				if (PluginState.STARTED.equals(state)) {
					stdout.println(String.format("Started %s", pluginWrapper.getPluginId()));
				} else {
					throw new Failure(1, String.format("Failed to start %s", pluginWrapper.getPluginId()));
				}
			}
		}
	}

	@CommandMetaData(name = "stop", description = "Stop a plugin")
	public static class StopPlugin extends PluginCommand {

		@Argument(index = 0, required = true, metaVar = "ALL|<id>", usage = "the plugin to stop")
		protected String id;

		@Override
		public void run() throws Failure {
			IGitblit gitblit = getContext().getGitblit();
			if (id.equalsIgnoreCase("ALL")) {
				gitblit.stopPlugins();
				stdout.println("All plugins stopped");
			} else {
				PluginWrapper pluginWrapper = getPlugin(id);
				if (pluginWrapper == null) {
					throw new UnloggedFailure(String.format("Plugin %s is not installed!", id));
				}

				PluginState state = gitblit.stopPlugin(pluginWrapper.getPluginId());
				if (PluginState.STOPPED.equals(state)) {
					stdout.println(String.format("Stopped %s", pluginWrapper.getPluginId()));
				} else {
					throw new Failure(1, String.format("Failed to stop %s", pluginWrapper.getPluginId()));
				}
			}
		}
	}

	@CommandMetaData(name = "enable", description = "Enable a plugin")
	public static class EnablePlugin extends PluginCommand {

		@Argument(index = 0, required = true, metaVar = "<id>", usage = "the plugin id to enable")
		protected String id;

		@Override
		public void run() throws Failure {
			IGitblit gitblit = getContext().getGitblit();
			PluginWrapper pluginWrapper = getPlugin(id);
			if (pluginWrapper == null) {
				throw new UnloggedFailure("Invalid plugin specified!");
			}

			if (gitblit.enablePlugin(pluginWrapper.getPluginId())) {
				stdout.println(String.format("Enabled %s", pluginWrapper.getPluginId()));
			} else {
				throw new Failure(1, String.format("Failed to enable %s", pluginWrapper.getPluginId()));
			}
		}
	}

	@CommandMetaData(name = "disable", description = "Disable a plugin")
	public static class DisablePlugin extends PluginCommand {

		@Argument(index = 0, required = true, metaVar = "<id>", usage = "the plugin to disable")
		protected String id;

		@Override
		public void run() throws Failure {
			IGitblit gitblit = getContext().getGitblit();
			PluginWrapper pluginWrapper = getPlugin(id);
			if (pluginWrapper == null) {
				throw new UnloggedFailure("Invalid plugin specified!");
			}

			if (gitblit.disablePlugin(pluginWrapper.getPluginId())) {
				stdout.println(String.format("Disabled %s", pluginWrapper.getPluginId()));
			} else {
				throw new Failure(1, String.format("Failed to disable %s", pluginWrapper.getPluginId()));
			}
		}
	}

	@CommandMetaData(name = "show", description = "Show the details of a plugin")
	public static class ShowPlugin extends PluginCommand {

		@Argument(index = 0, required = true, metaVar = "<id>", usage = "the plugin to show")
		protected String id;

		@Override
		public void run() throws Failure {
			IGitblit gitblit = getContext().getGitblit();
			PluginWrapper pw = getPlugin(id);
			if (pw == null) {
				PluginRegistration registration = gitblit.lookupPlugin(id);
				if (registration == null) {
					throw new Failure(1, String.format("Unknown plugin %s", id));
				}
				show(registration);
			} else {
				show(pw);
			}
		}

		protected String buildFieldTable(PluginWrapper pw, PluginRegistration reg) {
			final String id = pw == null ? reg.id : pw.getPluginId();
			final String name = reg == null ? "" : reg.name;
			final String version = pw == null ? "" : pw.getDescriptor().getVersion().toString();
			final String provider = pw == null ? reg.provider : pw.getDescriptor().getProvider();
			final String registry = reg == null ? "" : reg.registry;
			final String path = pw == null ? "" : pw.getPluginPath();
			final String projectUrl = reg == null ? "" : reg.projectUrl;
			final String state;
			if (pw == null) {
				// plugin could be installed
				state = InstallState.NOT_INSTALLED.toString();
			} else if (reg == null) {
				// unregistered, installed plugin
				state = Joiner.on(", ").join(InstallState.INSTALLED, pw.getPluginState());
			} else {
				// registered, installed plugin
				state = Joiner.on(", ").join(reg.getInstallState(), pw.getPluginState());
			}

			StringBuilder sb = new StringBuilder();
			sb.append("ID          : ").append(id).append('\n');
			sb.append("Version     : ").append(version).append('\n');
			sb.append("State       : ").append(state).append('\n');
			sb.append("Path        : ").append(path).append('\n');
			sb.append('\n');
			sb.append("Name        : ").append(name).append('\n');
			sb.append("Provider    : ").append(provider).append('\n');
			sb.append("Project URL : ").append(projectUrl).append('\n');
			sb.append("Registry    : ").append(registry).append('\n');

			return sb.toString();
		}

		protected String buildReleaseTable(PluginRegistration reg) {
			List<PluginRelease> releases = reg.releases;
			Collections.sort(releases);
			String releaseTable;
			if (releases.isEmpty()) {
				releaseTable = FlipTable.EMPTY;
			} else {
				String[] headers = { "Version", "Date", "Requires" };
				Object[][] data = new Object[releases.size()][];
				for (int i = 0; i < releases.size(); i++) {
					PluginRelease release = releases.get(i);
					data[i] = new Object[] { (release.version.equals(reg.installedRelease) ? ">" : " ") + release.version,
							release.date, release.requires };
				}
				releaseTable = FlipTable.of(headers, data, Borders.COLS);
			}
			return releaseTable;
		}

		/**
		 * Show an uninstalled plugin.
		 *
		 * @param reg
		 */
		protected void show(PluginRegistration reg) {
			// REGISTRATION
			final String fields = buildFieldTable(null, reg);
			final String releases = buildReleaseTable(reg);

			String[] headers = { reg.id };
			Object[][] data = new Object[3][];
			data[0] = new Object[] { fields };
			data[1] = new Object[] { "RELEASES" };
			data[2] = new Object[] { releases };
			stdout.println(FlipTable.of(headers, data));
		}

		/**
		 * Show an installed plugin.
		 *
		 * @param pw
		 */
		protected void show(PluginWrapper pw) {
			IGitblit gitblit = getContext().getGitblit();
			PluginRegistration reg = gitblit.lookupPlugin(pw.getPluginId());

			// FIELDS
			final String fields = buildFieldTable(pw, reg);

			// EXTENSIONS
			StringBuilder sb = new StringBuilder();
			List<Class<?>> exts = gitblit.getExtensionClasses(pw.getPluginId());
			String extensions;
			if (exts.isEmpty()) {
				extensions = FlipTable.EMPTY;
			} else {
				StringBuilder description = new StringBuilder();
				for (int i = 0; i < exts.size(); i++) {
					Class<?> ext = exts.get(i);
					if (ext.isAnnotationPresent(CommandMetaData.class)) {
						CommandMetaData meta = ext.getAnnotation(CommandMetaData.class);
						description.append(meta.name());
						if (meta.description().length() > 0) {
							description.append(": ").append(meta.description());
						}
						description.append('\n');
					}
					description.append(ext.getName()).append("\n  └ ");
					description.append(getExtensionPoint(ext).getName());
					description.append("\n\n");
				}
				extensions = description.toString();
			}

			// DEPENDENCIES
			sb.setLength(0);
			List<PluginDependency> deps = pw.getDescriptor().getDependencies();
			String dependencies;
			if (deps.isEmpty()) {
				dependencies = FlipTable.EMPTY;
			} else {
				String[] headers = { "Id", "Version" };
				Object[][] data = new Object[deps.size()][];
				for (int i = 0; i < deps.size(); i++) {
					PluginDependency dep = deps.get(i);
					data[i] = new Object[] { dep.getPluginId(), dep.getPluginVersion() };
				}
				dependencies = FlipTable.of(headers, data, Borders.COLS);
			}

			// RELEASES
			String releases;
			if (reg == null) {
				releases = FlipTable.EMPTY;
			} else {
				releases = buildReleaseTable(reg);
			}

			String[] headers = { pw.getPluginId() };
			Object[][] data = new Object[7][];
			data[0] = new Object[] { fields };
			data[1] = new Object[] { "EXTENSIONS" };
			data[2] = new Object[] { extensions };
			data[3] = new Object[] { "DEPENDENCIES" };
			data[4] = new Object[] { dependencies };
			data[5] = new Object[] { "RELEASES" };
			data[6] = new Object[] { releases };
			stdout.println(FlipTable.of(headers, data));
		}

		/* Find the ExtensionPoint */
		protected Class<?> getExtensionPoint(Class<?> clazz) {
			Class<?> superClass = clazz.getSuperclass();
			if (ExtensionPoint.class.isAssignableFrom(superClass)) {
				return superClass;
			}
			return getExtensionPoint(superClass);
		}
	}

	@CommandMetaData(name = "refresh", description = "Refresh the plugin registry data")
	public static class RefreshPlugins extends SshCommand {
		@Override
		public void run() throws Failure {
			IGitblit gitblit = getContext().getGitblit();
			gitblit.refreshRegistry();
		}
	}

	@CommandMetaData(name = "available", description = "List the available plugins")
	public static class AvailablePlugins extends ListFilterCommand<PluginRegistration> {

		@Option(name = "--refresh", aliases = { "-r" }, usage = "refresh the plugin registry")
		protected boolean refresh;

		@Option(name = "--updates", aliases = { "-u" }, usage = "show available updates")
		protected boolean updates;

		@Override
		protected List<PluginRegistration> getItems() throws UnloggedFailure {
			IGitblit gitblit = getContext().getGitblit();
			if (refresh) {
				gitblit.refreshRegistry();
			}

			List<PluginRegistration> list;
			if (updates) {
				list = gitblit.getRegisteredPlugins(InstallState.CAN_UPDATE);
			} else {
				list = gitblit.getRegisteredPlugins();
			}
			return list;
		}

		@Override
		protected boolean matches(String filter, PluginRegistration t) {
			return t.id.matches(filter) || t.name.matches(filter);
		}

		@Override
		protected void asTable(List<PluginRegistration> list) {
			String[] headers;
			if (verbose) {
				String [] h = { "Id", "Name", "Description", "Installed", "Current", "Requires", "State", "Registry" };
				headers = h;
			} else {
				String [] h = { "Id", "Name", "Installed", "Current", "Requires", "State" };
				headers = h;
			}
			Object[][] data = new Object[list.size()][];
			for (int i = 0; i < list.size(); i++) {
				PluginRegistration p = list.get(i);
				PluginRelease curr = p.getCurrentRelease();
				if (verbose) {
					data[i] = new Object[] {p.id, p.name, p.description, p.installedRelease, curr.version, curr.requires, p.getInstallState(), p.registry};
				} else {
					data[i] = new Object[] {p.id, p.name, p.installedRelease, curr.version, curr.requires, p.getInstallState()};
				}
			}

			stdout.println(FlipTable.of(headers, data, Borders.BODY_HCOLS));
		}

		@Override
		protected void asTabbed(List<PluginRegistration> list) {
			for (PluginRegistration p : list) {
				PluginRelease curr = p.getCurrentRelease();
				if (verbose) {
					outTabbed(p.id, p.name, p.description, p.installedRelease, curr.version, curr.requires, p.getInstallState(), p.provider, p.registry);
				} else {
					outTabbed(p.id, p.name, p.installedRelease, curr.version, curr.requires, p.getInstallState());
				}
			}
		}
	}

	@CommandMetaData(name = "install", description = "Download and installs a plugin")
	public static class InstallPlugin extends SshCommand {

		@Argument(index = 0, required = true, metaVar = "<URL>|<ID>|<NAME>", usage = "the id, name, or the url of the plugin to download and install")
		protected String urlOrIdOrName;

		@Option(name = "--version", usage = "The specific version to install")
		private String version;

		@Option(name = "--noverify", usage = "Disable checksum verification")
		private boolean disableChecksum;

		@Override
		public void run() throws Failure {
			IGitblit gitblit = getContext().getGitblit();
			try {
				String ulc = urlOrIdOrName.toLowerCase();
				if (ulc.startsWith("http://") || ulc.startsWith("https://")) {
					if (gitblit.installPlugin(urlOrIdOrName, !disableChecksum)) {
						stdout.println(String.format("Installed %s", urlOrIdOrName));
					} else {
						new Failure(1, String.format("Failed to install %s", urlOrIdOrName));
					}
				} else {
					PluginRelease pv = gitblit.lookupRelease(urlOrIdOrName, version);
					if (pv == null) {
						throw new Failure(1,  String.format("Plugin \"%s\" is not in the registry!", urlOrIdOrName));
					}
					if (gitblit.installPlugin(pv.url, !disableChecksum)) {
						stdout.println(String.format("Installed %s", urlOrIdOrName));
					} else {
						throw new Failure(1, String.format("Failed to install %s", urlOrIdOrName));
					}
				}
			} catch (Exception e) {
				log.error("Failed to install " + urlOrIdOrName, e);
				throw new Failure(1, String.format("Failed to install %s", urlOrIdOrName), e);
			}
		}
	}

	@CommandMetaData(name = "uninstall", aliases = { "rm", "del" }, description = "Uninstall a plugin")
	public static class UninstallPlugin extends PluginCommand {

		@Argument(index = 0, required = true, metaVar = "<id>", usage = "the plugin to uninstall")
		protected String id;

		@Override
		public void run() throws Failure {
			IGitblit gitblit = getContext().getGitblit();
			PluginWrapper pluginWrapper = getPlugin(id);
			if (pluginWrapper == null) {
				throw new UnloggedFailure(String.format("Plugin %s is not installed!", id));
			}

			if (gitblit.deletePlugin(pluginWrapper.getPluginId())) {
				stdout.println(String.format("Uninstalled %s", pluginWrapper.getPluginId()));
			} else {
				throw new Failure(1, String.format("Failed to uninstall %s", pluginWrapper.getPluginId()));
			}
		}
	}
}
