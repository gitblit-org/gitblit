package com.gitblit.transport.ssh.gitblit;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.parboiled.common.StringUtils;

import com.gitblit.manager.IGitblit;
import com.gitblit.models.ServerSettings;
import com.gitblit.models.SettingModel;
import com.gitblit.transport.ssh.commands.CommandMetaData;
import com.gitblit.transport.ssh.commands.SshCommand;
import com.gitblit.transport.ssh.commands.UsageExample;
import com.gitblit.transport.ssh.commands.UsageExamples;
import com.google.common.collect.Maps;

@CommandMetaData(name = "config", description = "Administer Gitblit settings", admin = true)
@UsageExamples(examples = {
		@UsageExample(syntax = "${cmd} --list", description = "List all settings"),
		@UsageExample(syntax = "${cmd} git.sshPort", description = "Describe the git.sshPort setting"),
		@UsageExample(syntax = "${cmd} git.sshPort 29418", description = "Set git.sshPort to 29418"),
		@UsageExample(syntax = "${cmd} git.sshPort --reset", description = "Reset git.sshPort to it's default value"),
})
public class ConfigCommand extends SshCommand {

	@Argument(index = 0, metaVar = "KEY", usage = "The setting to describe or update")
	protected String setting;

	@Argument(index = 1, metaVar = "VALUE", usage = "The new value for the setting")
	protected String value;

	@Option(name = "--list", aliases = { "-l" }, usage = "List all settings")
	private boolean listAll;

	@Option(name = "--modified", aliases = { "-m" }, usage = "List modified settings")
	private boolean listModified;

	@Option(name = "--reset", usage = "Reset a setting to it's default value")
	private boolean reset;

	@Override
	public void run() throws UnloggedFailure {
		IGitblit gitblit = getContext().getGitblit();
		ServerSettings settings = gitblit.getSettingsModel();

		if (listAll || listModified) {
			/*
			 *  List settings
			 */
			List<SettingModel> list = new ArrayList<SettingModel>();
			int maxLen = 0;
			for (String key : settings.getKeys()) {
				SettingModel model = settings.get(key);
				if (listModified) {
					if (!model.isDefaultValue()) {
						list.add(model);
					} else {
						continue;
					}
				} else {
					list.add(model);
				}

				if (key.length() > maxLen) {
					maxLen = key.length();
				}
			}
			String pattern = MessageFormat.format("%s%-{0,number,0}s : %s", maxLen);
			for (SettingModel model : list) {
				stdout.println(String.format(pattern,
						model.isDefaultValue() ? " " : "*",
						model.name,
						model.currentValue));
			}
		} else if (!StringUtils.isEmpty(setting) && value == null && !reset) {
			/*
			 *  Describe a setting
			 */
			SettingModel model = settings.get(setting);
			if (model == null) {
				// unknown setting
				String value = gitblit.getSettings().getString(setting, null);
				if (value == null) {
					// setting does not exist, can not describe
					stdout.println(String.format("\"%s\" is not a valid setting.", setting));
					return;
				}

				model = new SettingModel();
				model.defaultValue = "";
				model.currentValue = value;
			}
			stdout.println();
			stdout.println(model.name);
			if (!StringUtils.isEmpty(model.since)) {
				stdout.println(SettingModel.SINCE + " " + model.since);
			}
			if (model.restartRequired) {
				stdout.println(SettingModel.RESTART_REQUIRED);
			}
			if (model.spaceDelimited) {
				stdout.println(SettingModel.SPACE_DELIMITED);
			}
			if (!StringUtils.isEmpty(model.description)) {
				stdout.println();
				stdout.println(model.description);
			}
			stdout.println();
			if (model.defaultValue != null) {
				stdout.println("default: " + model.defaultValue);
			}
			if (!model.isDefaultValue()) {
				stdout.println("current: " + model.currentValue);
			} else {
				stdout.println("current: <default>");
			}
			stdout.println();
		} else if (!StringUtils.isEmpty(setting) && value == null && reset) {
			/*
			 *  Reset a setting
			 */
			SettingModel model = settings.get(setting);
			if (model == null) {
				stdout.println(String.format("\"%s\" is not a valid setting.", setting));
				return;
			}

			if (model.defaultValue == null || model.defaultValue.equals("null")) {
				// no default value, remove setting
				gitblit.getSettings().removeSetting(setting);
				gitblit.getSettings().saveSettings();
				settings.remove(setting);

				stdout.println(String.format("%s removed.", setting));
			} else {
				// reset to default value
				Map<String, String> updates = Maps.newHashMap();
				updates.put(setting, model.defaultValue == null ? "" : model.defaultValue);
				gitblit.getSettings().saveSettings(updates);

				// confirm reset
				String newValue = gitblit.getSettings().getString(setting, null);
				if (model.defaultValue.equals(newValue)) {
					stdout.println(String.format("%s reset to the default value.", setting));
				} else {
					stdout.println(String.format("failed to reset %s!", setting));
				}
			}

		} else if (!StringUtils.isEmpty(setting) && value != null) {
			/*
			 *  Update a setting
			 */
			Map<String, String> updates = Maps.newHashMap();
			updates.put(setting, value);
			gitblit.getSettings().saveSettings(updates);

			// confirm update
			String newValue = gitblit.getSettings().getString(setting, null);
			if (value.equals(newValue)) {
				stdout.println(String.format("%s updated.", setting));
			} else {
				stdout.println(String.format("failed to update %s!", setting));
			}
		} else {
			// Display usage
			showHelp();
		}
	}
}