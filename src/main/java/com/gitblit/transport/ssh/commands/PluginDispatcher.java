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

import java.util.ArrayList;
import java.util.List;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import ro.fortsoft.pf4j.PluginDependency;
import ro.fortsoft.pf4j.PluginDescriptor;
import ro.fortsoft.pf4j.PluginState;
import ro.fortsoft.pf4j.PluginWrapper;

import com.gitblit.manager.IGitblit;
import com.gitblit.models.PluginRegistry.PluginRegistration;
import com.gitblit.models.PluginRegistry.PluginRelease;
import com.gitblit.models.UserModel;
import com.gitblit.utils.FlipTable;
import com.gitblit.utils.FlipTable.Borders;

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
		register(user, ShowPlugin.class);
		register(user, RemovePlugin.class);
		register(user, InstallPlugin.class);
		register(user, AvailablePlugins.class);
	}

	@CommandMetaData(name = "list", aliases = { "ls" }, description = "List the loaded plugins")
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
				String [] h = { "#", "Id", "Version", "State", "Mode", "Path", "Provider"};
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
					data[i] = new Object[] { "" + (i + 1), d.getPluginId(), d.getVersion(), p.getPluginState(), p.getRuntimeMode(), p.getPluginPath(), d.getProvider() };
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
					outTabbed(d.getPluginId(), d.getVersion(), pw.getPluginState(), pw.getRuntimeMode(), pw.getPluginPath(), d.getProvider());
				} else {
					outTabbed(d.getPluginId(), d.getVersion(), pw.getPluginState(), pw.getPluginPath());
				}
			}
		}
	}

	@CommandMetaData(name = "start", description = "Start a plugin")
	public static class StartPlugin extends SshCommand {

		@Argument(index = 0, required = true, metaVar = "ALL|<id>", usage = "the plugin to start")
		protected String plugin;

		@Override
		public void run() throws UnloggedFailure {
			IGitblit gitblit = getContext().getGitblit();
			if (plugin.equalsIgnoreCase("ALL")) {
				gitblit.startPlugins();
				stdout.println("All plugins started");
			} else {
				try {
					int index = Integer.parseInt(plugin);
					List<PluginWrapper> plugins = gitblit.getPlugins();
					if (index > plugins.size()) {
						throw new UnloggedFailure(1,  "Invalid plugin index specified!");
					}
					PluginWrapper pw = plugins.get(index - 1);
					start(pw);
				} catch (NumberFormatException n) {
					for (PluginWrapper pw : gitblit.getPlugins()) {
						PluginDescriptor pd = pw.getDescriptor();
						if (pd.getPluginId().equalsIgnoreCase(plugin)) {
							start(pw);
							break;
						}
					}
				}
			}
		}

		protected void start(PluginWrapper pw) throws UnloggedFailure {
			String id = pw.getDescriptor().getPluginId();
			if (pw.getPluginState() == PluginState.STARTED) {
				throw new UnloggedFailure(1, String.format("%s is already started.", id));
			}
			try {
				pw.getPlugin().start();
//            	pw.setPluginState(PluginState.STARTED);
				stdout.println(String.format("%s started", id));
			} catch (Exception pe) {
				throw new UnloggedFailure(1, String.format("Failed to start %s", id), pe);
			}
		}
	}


	@CommandMetaData(name = "stop", description = "Stop a plugin")
	public static class StopPlugin extends SshCommand {

		@Argument(index = 0, required = true, metaVar = "ALL|<id>", usage = "the plugin to stop")
		protected String plugin;

		@Override
		public void run() throws UnloggedFailure {
			IGitblit gitblit = getContext().getGitblit();
			if (plugin.equalsIgnoreCase("ALL")) {
				gitblit.stopPlugins();
				stdout.println("All plugins stopped");
			} else {
				try {
				int index = Integer.parseInt(plugin);
				List<PluginWrapper> plugins = gitblit.getPlugins();
				if (index > plugins.size()) {
					throw new UnloggedFailure(1,  "Invalid plugin index specified!");
				}
				PluginWrapper pw = plugins.get(index - 1);
				stop(pw);
			} catch (NumberFormatException n) {
				for (PluginWrapper pw : gitblit.getPlugins()) {
					PluginDescriptor pd = pw.getDescriptor();
					if (pd.getPluginId().equalsIgnoreCase(plugin)) {
						stop(pw);
						break;
					}
				}
			}
			}
		}

		protected void stop(PluginWrapper pw) throws UnloggedFailure {
			String id = pw.getDescriptor().getPluginId();
			if (pw.getPluginState() == PluginState.STOPPED) {
				throw new UnloggedFailure(1, String.format("%s is already stopped.", id));
			}
			try {
				pw.getPlugin().stop();
//            	pw.setPluginState(PluginState.STOPPED);
				stdout.println(String.format("%s stopped", id));
			} catch (Exception pe) {
				throw new UnloggedFailure(1, String.format("Failed to stop %s", id), pe);
			}
		}
	}

	@CommandMetaData(name = "show", description = "Show the details of a plugin")
	public static class ShowPlugin extends SshCommand {

		@Argument(index = 0, required = true, metaVar = "<id>", usage = "the plugin to stop")
		protected int index;

		@Override
		public void run() throws UnloggedFailure {
			IGitblit gitblit = getContext().getGitblit();
			List<PluginWrapper> plugins = gitblit.getPlugins();
			if (index > plugins.size()) {
				throw new UnloggedFailure(1, "Invalid plugin index specified!");
			}
			PluginWrapper pw = plugins.get(index - 1);
			PluginDescriptor d = pw.getDescriptor();

			// FIELDS
			StringBuilder sb = new StringBuilder();
			sb.append("Version  : ").append(d.getVersion()).append('\n');
			sb.append("Provider : ").append(d.getProvider()).append('\n');
			sb.append("Path     : ").append(pw.getPluginPath()).append('\n');
			sb.append("State    : ").append(pw.getPluginState()).append('\n');
			final String fields = sb.toString();

			// TODO EXTENSIONS
			sb.setLength(0);
			List<String> exts = new ArrayList<String>();
			String extensions;
			if (exts.isEmpty()) {
				extensions = FlipTable.EMPTY;
			} else {
				String[] headers = { "Id", "Version" };
				Object[][] data = new Object[exts.size()][];
				for (int i = 0; i < exts.size(); i++) {
					String ext = exts.get(i);
					data[0] = new Object[] { ext.toString(), ext.toString() };
				}
				extensions = FlipTable.of(headers, data, Borders.COLS);
			}

			// DEPENDENCIES
			sb.setLength(0);
			List<PluginDependency> deps = d.getDependencies();
			String dependencies;
			if (deps.isEmpty()) {
				dependencies = FlipTable.EMPTY;
			} else {
				String[] headers = { "Id", "Version" };
				Object[][] data = new Object[deps.size()][];
				for (int i = 0; i < deps.size(); i++) {
					PluginDependency dep = deps.get(i);
					data[0] = new Object[] { dep.getPluginId(), dep.getPluginVersion() };
				}
				dependencies = FlipTable.of(headers, data, Borders.COLS);
			}

			String[] headers = { d.getPluginId() };
			Object[][] data = new Object[5][];
			data[0] = new Object[] { fields };
			data[1] = new Object[] { "EXTENSIONS" };
			data[2] = new Object[] { extensions };
			data[3] = new Object[] { "DEPENDENCIES" };
			data[4] = new Object[] { dependencies };
			stdout.println(FlipTable.of(headers, data));
		}
	}

	@CommandMetaData(name = "remove", aliases= { "rm", "del" }, description = "Remove a plugin", hidden = true)
	public static class RemovePlugin extends SshCommand {

		@Argument(index = 0, required = true, metaVar = "<id>", usage = "the plugin to stop")
		protected int index;

		@Override
		public void run() throws UnloggedFailure {
			IGitblit gitblit = getContext().getGitblit();
			List<PluginWrapper> plugins = gitblit.getPlugins();
			if (index > plugins.size()) {
				throw new UnloggedFailure(1, "Invalid plugin index specified!");
			}
			PluginWrapper pw = plugins.get(index - 1);
			PluginDescriptor d = pw.getDescriptor();
			if (gitblit.deletePlugin(pw)) {
				stdout.println(String.format("Deleted %s %s", d.getPluginId(), d.getVersion()));
			} else {
				throw new UnloggedFailure(1,  String.format("Failed to delete %s %s", d.getPluginId(), d.getVersion()));
			}
		}
	}

	@CommandMetaData(name = "install", description = "Download and installs a plugin", hidden = true)
	public static class InstallPlugin extends SshCommand {

		@Argument(index = 0, required = true, metaVar = "<URL>|<ID>|<NAME>", usage = "the id, name, or the url of the plugin to download and install")
		protected String urlOrIdOrName;

		@Option(name = "--version", usage = "The specific version to install")
		private String version;

		@Override
		public void run() throws UnloggedFailure {
			IGitblit gitblit = getContext().getGitblit();
			try {
				String ulc = urlOrIdOrName.toLowerCase();
				if (ulc.startsWith("http://") || ulc.startsWith("https://")) {
					if (gitblit.installPlugin(urlOrIdOrName)) {
						stdout.println(String.format("Installed %s", urlOrIdOrName));
					} else {
						new UnloggedFailure(1, String.format("Failed to install %s", urlOrIdOrName));
					}
				} else {
					PluginRelease pv = gitblit.lookupRelease(urlOrIdOrName, version);
					if (pv == null) {
						throw new UnloggedFailure(1,  String.format("Plugin \"%s\" is not in the registry!", urlOrIdOrName));
					}
					if (gitblit.installPlugin(pv)) {
						stdout.println(String.format("Installed %s", urlOrIdOrName));
					} else {
						throw new UnloggedFailure(1, String.format("Failed to install %s", urlOrIdOrName));
					}
				}
			} catch (Exception e) {
				log.error("Failed to install " + urlOrIdOrName, e);
				throw new UnloggedFailure(1, String.format("Failed to install %s", urlOrIdOrName), e);
			}
		}
	}

	@CommandMetaData(name = "available", description = "List the available plugins")
	public static class AvailablePlugins extends ListFilterCommand<PluginRegistration> {

		@Option(name = "--refresh", aliases = { "-r" }, usage = "refresh the plugin registry")
		protected boolean refresh;

		@Override
		protected List<PluginRegistration> getItems() throws UnloggedFailure {
			IGitblit gitblit = getContext().getGitblit();
			if (refresh) {
				gitblit.refreshRegistry();
			}
			List<PluginRegistration> list = gitblit.getRegisteredPlugins();
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
				String [] h = { "Name", "Description", "Installed", "Release", "State", "Id", "Provider" };
				headers = h;
			} else {
				String [] h = { "Name", "Description", "Installed", "Release", "State" };
				headers = h;
			}
			Object[][] data = new Object[list.size()][];
			for (int i = 0; i < list.size(); i++) {
				PluginRegistration p = list.get(i);
				if (verbose) {
					data[i] = new Object[] {p.name, p.description, p.installedRelease, p.currentRelease, p.getInstallState(), p.id, p.provider};
				} else {
					data[i] = new Object[] {p.name, p.description, p.installedRelease, p.currentRelease, p.getInstallState()};
				}
			}

			stdout.println(FlipTable.of(headers, data, Borders.BODY_HCOLS));
		}

		@Override
		protected void asTabbed(List<PluginRegistration> list) {
			for (PluginRegistration p : list) {
				if (verbose) {
					outTabbed(p.name, p.description, p.currentRelease, p.getInstallState(), p.id, p.provider);
				} else {
					outTabbed(p.name, p.description, p.currentRelease, p.getInstallState());
				}
			}
		}
	}
}
