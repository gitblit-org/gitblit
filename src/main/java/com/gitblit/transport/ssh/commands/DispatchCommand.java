// Copyright (C) 2009 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.gitblit.transport.ssh.commands;

import java.io.IOException;
import java.io.StringWriter;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.sshd.server.Command;
import org.apache.sshd.server.Environment;
import org.kohsuke.args4j.Argument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ro.fortsoft.pf4j.ExtensionPoint;

import com.gitblit.models.UserModel;
import com.gitblit.utils.StringUtils;
import com.gitblit.utils.cli.SubcommandHandler;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;

public abstract class DispatchCommand extends BaseCommand implements ExtensionPoint {

	private Logger log = LoggerFactory.getLogger(getClass());

	@Argument(index = 0, required = false, metaVar = "COMMAND", handler = SubcommandHandler.class)
	private String commandName;

	@Argument(index = 1, multiValued = true, metaVar = "ARG")
	private List<String> args = new ArrayList<String>();

	private final Set<Class<? extends BaseCommand>> commands;
	private final Map<String, DispatchCommand> dispatchers;
	private final Map<String, String> aliasToCommand;
	private final Map<String, List<String>> commandToAliases;
	private final List<BaseCommand> instantiated;
	private Map<String, Class<? extends BaseCommand>> map;

	protected DispatchCommand() {
		commands = new HashSet<Class<? extends BaseCommand>>();
		dispatchers = Maps.newHashMap();
		aliasToCommand = Maps.newHashMap();
		commandToAliases = Maps.newHashMap();
		instantiated = new ArrayList<BaseCommand>();
	}

	@Override
	public void destroy() {
		super.destroy();
		commands.clear();
		aliasToCommand.clear();
		commandToAliases.clear();
		map = null;

		for (BaseCommand command : instantiated) {
			command.destroy();
		}
		instantiated.clear();

		for (DispatchCommand dispatcher : dispatchers.values()) {
			dispatcher.destroy();
		}
		dispatchers.clear();
	}

	protected void registerDispatcher(UserModel user, Class<? extends DispatchCommand> cmd) {
		try {
			DispatchCommand dispatcher = cmd.newInstance();
			registerDispatcher(user, dispatcher);
		} catch (Exception e) {
			log.error("failed to instantiate {}", cmd.getName());
		}
	}

	protected void registerDispatcher(UserModel user, DispatchCommand dispatcher) {
		Class<? extends DispatchCommand> dispatcherClass = dispatcher.getClass();
		if (!dispatcherClass.isAnnotationPresent(CommandMetaData.class)) {
			throw new RuntimeException(MessageFormat.format("{0} must be annotated with {1}!", dispatcher.getName(),
					CommandMetaData.class.getName()));
		}

		CommandMetaData meta = dispatcherClass.getAnnotation(CommandMetaData.class);
		if (meta.admin() && !user.canAdmin()) {
			log.debug(MessageFormat.format("excluding admin dispatcher {0} for {1}", meta.name(), user.username));
			return;
		}

		log.debug("registering {} dispatcher", meta.name());
		try {
			dispatcher.registerCommands(user);
			dispatchers.put(meta.name(), dispatcher);
			for (String alias : meta.aliases()) {
				aliasToCommand.put(alias, meta.name());
				if (!commandToAliases.containsKey(meta.name())) {
					commandToAliases.put(meta.name(), new ArrayList<String>());
				}
				commandToAliases.get(meta.name()).add(alias);
			}
		} catch (Exception e) {
			log.error("failed to register {} dispatcher", meta.name());
		}
	}


	protected abstract void registerCommands(UserModel user);

	/**
	 * Registers a command as long as the user is permitted to execute it.
	 *
	 * @param user
	 * @param cmd
	 */
	protected void registerCommand(UserModel user, Class<? extends BaseCommand> cmd) {
		if (!cmd.isAnnotationPresent(CommandMetaData.class)) {
			throw new RuntimeException(MessageFormat.format("{0} must be annotated with {1}!", cmd.getName(),
					CommandMetaData.class.getName()));
		}
		CommandMetaData meta = cmd.getAnnotation(CommandMetaData.class);
		if (meta.admin() && !user.canAdmin()) {
			log.debug(MessageFormat.format("excluding admin command {0} for {1}", meta.name(), user.username));
			return;
		}
		commands.add(cmd);
	}

	private Map<String, Class<? extends BaseCommand>> getMap() {
		if (map == null) {
			map = Maps.newHashMapWithExpectedSize(commands.size());
			for (Class<? extends BaseCommand> cmd : commands) {
				CommandMetaData meta = cmd.getAnnotation(CommandMetaData.class);
				if (map.containsKey(meta.name()) || aliasToCommand.containsKey(meta.name())) {
					log.warn("{} already contains the \"{}\" command!", getName(), meta.name());
				} else {
					map.put(meta.name(), cmd);
				}
				for (String alias : meta.aliases()) {
					if (map.containsKey(alias) || aliasToCommand.containsKey(alias)) {
						log.warn("{} already contains the \"{}\" command!", getName(), alias);
					} else {
						aliasToCommand.put(alias, meta.name());
						if (!commandToAliases.containsKey(meta.name())) {
							commandToAliases.put(meta.name(), new ArrayList<String>());
						}
						commandToAliases.get(meta.name()).add(alias);
					}
				}
			}

			for (Map.Entry<String, DispatchCommand> entry : dispatchers.entrySet()) {
				map.put(entry.getKey(), entry.getValue().getClass());
			}
		}
		return map;
	}

	@Override
	public void start(Environment env) throws IOException {
		try {
			parseCommandLine();
			if (Strings.isNullOrEmpty(commandName)) {
				StringWriter msg = new StringWriter();
				msg.write(usage());
				throw new UnloggedFailure(1, msg.toString());
			}

			BaseCommand cmd = getCommand();
			if (getName().isEmpty()) {
				cmd.setName(commandName);
			} else {
				cmd.setName(getName() + " " + commandName);
			}
			cmd.setArguments(args.toArray(new String[args.size()]));

			provideStateTo(cmd);
			// atomicCmd.set(cmd);
			cmd.start(env);

		} catch (UnloggedFailure e) {
			String msg = e.getMessage();
			if (!msg.endsWith("\n")) {
				msg += "\n";
			}
			err.write(msg.getBytes(Charsets.UTF_8));
			err.flush();
			exit.onExit(e.exitCode);
		}
	}

	private BaseCommand getCommand() throws UnloggedFailure {
		Map<String, Class<? extends BaseCommand>> map = getMap();
		String name = commandName;
		if (aliasToCommand.containsKey(commandName)) {
			name = aliasToCommand.get(name);
		}
		if (dispatchers.containsKey(name)) {
			return dispatchers.get(name);
		}
		final Class<? extends BaseCommand> c = map.get(name);
		if (c == null) {
			String msg = (getName().isEmpty() ? "Gitblit" : getName()) + ": " + commandName + ": not found";
			throw new UnloggedFailure(1, msg);
		}

		BaseCommand cmd = null;
		try {
			cmd = c.newInstance();
			instantiated.add(cmd);
		} catch (Exception e) {
			throw new UnloggedFailure(1, MessageFormat.format("Failed to instantiate {0} command", commandName));
		}
		return cmd;
	}

	@Override
	public String usage() {
		Set<String> commands = new TreeSet<String>();
		Set<String> dispatchers = new TreeSet<String>();
		Map<String, String> displayNames = Maps.newHashMap();
		int maxLength = -1;
		Map<String, Class<? extends BaseCommand>> m = getMap();
		for (String name : m.keySet()) {
			Class<? extends BaseCommand> c = m.get(name);
			CommandMetaData meta = c.getAnnotation(CommandMetaData.class);
			if (meta.hidden()) {
				continue;
			}

			String displayName = name;
			if (commandToAliases.containsKey(meta.name())) {
				displayName = name + " (" + Joiner.on(',').join(commandToAliases.get(meta.name())) + ")";
			}
			displayNames.put(name, displayName);

			maxLength = Math.max(maxLength, displayName.length());
			if (DispatchCommand.class.isAssignableFrom(c)) {
				dispatchers.add(name);
			} else {
				commands.add(name);
			}
		}
		String format = "%-" + maxLength + "s   %s";

		final StringBuilder usage = new StringBuilder();
		if (!commands.isEmpty()) {
			usage.append("Available commands");
			if (!getName().isEmpty()) {
				usage.append(" of ");
				usage.append(getName());
			}
			usage.append(" are:\n");
			usage.append("\n");
			for (String name : commands) {
				final Class<? extends Command> c = m.get(name);
				String displayName = displayNames.get(name);
				CommandMetaData meta = c.getAnnotation(CommandMetaData.class);
				usage.append("   ");
				usage.append(String.format(format, displayName, Strings.nullToEmpty(meta.description())));
				usage.append("\n");
			}
			usage.append("\n");
		}

		if (!dispatchers.isEmpty()) {
			usage.append("Available command dispatchers");
			if (!getName().isEmpty()) {
				usage.append(" of ");
				usage.append(getName());
			}
			usage.append(" are:\n");
			usage.append("\n");
			for (String name : dispatchers) {
				final Class<? extends BaseCommand> c = m.get(name);
				String displayName = displayNames.get(name);
				CommandMetaData meta = c.getAnnotation(CommandMetaData.class);
				usage.append("   ");
				usage.append(String.format(format, displayName, Strings.nullToEmpty(meta.description())));
				usage.append("\n");
			}
			usage.append("\n");
		}

		usage.append("See '");
		if (!StringUtils.isEmpty(getName())) {
			usage.append(getName());
			usage.append(' ');
		}
		usage.append("COMMAND --help' for more information.\n");
		usage.append("\n");
		return usage.toString();
	}
}
