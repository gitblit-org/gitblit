/*
 * Copyright 2019 Tue Ton
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
package com.gitblit.gradle;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.MessageFormat;
import java.util.Properties;

import groovy.util.ConfigObject;
import groovy.util.ConfigSlurper;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generates class containing "keys" to a properties file.
 */
public class KeysGenerator extends DefaultTask {

	private static Logger logger = LoggerFactory.getLogger(KeysGenerator.class.getName());
	
	private static final String INDENT = "\t"; //tab indentation of the generated source code

	File propertiesFile;
	String outputClass;
	File toDir;
	
	@InputFile
	public File getPropertiesFile() {
		return propertiesFile;
	}

	@Input
	public String getOutputClass() {
		return outputClass;
	}

	@OutputDirectory
	public File getToDir() {
		return toDir;
	}

	public void setPropertiesFile(File propertiesFile) {
		this.propertiesFile = propertiesFile;
	}

	public void setOutputClass(String outputClass) {
		this.outputClass = outputClass;
	}

	public void setToDir(File toDir) {
		this.toDir = toDir;
	}

	@TaskAction
	public void generateKeysClass() throws IOException {
		logger.info("KeysGenerator (" + outputClass + ")");
		logger.info("    input: " + propertiesFile.getCanonicalPath());
		final StringBuilder classContents = new StringBuilder();
		generateKeysClassSource(classContents, propertiesFile, outputClass);
		writeKeysClass(toDir, outputClass, classContents);
	}

	private void writeKeysClass(File toDir, String outputClass, final StringBuilder classContents)
			throws IOException {
		String classFile = outputClass.replace('.', File.separatorChar) + ".java";
		File outFile = new File(toDir, classFile);
		outFile.getParentFile().mkdirs();
		try (BufferedWriter bw = Files.newBufferedWriter(outFile.toPath(), StandardCharsets.UTF_8)) {
			bw.write(classContents.toString());
		}
		logger.info("    output: " + outFile.getCanonicalPath());
	}

	private void generateKeysClassSource(final StringBuilder sb, File propertiesFile, String outputClass)
			throws FileNotFoundException, IOException {
		Properties props = new Properties();
		try (BufferedReader br = Files.newBufferedReader(propertiesFile.toPath(), StandardCharsets.UTF_8)) {
			props.load(br);
		}

		ConfigObject config = new ConfigSlurper().parse(props);

		int lastDotIndex = outputClass.lastIndexOf(".");
		String packageName = outputClass.substring(0, lastDotIndex);
		String className = outputClass.substring(lastDotIndex + 1);

		sb.append(MessageFormat.format("package {0};\n\n", packageName))
		  .append("/*\n")
		  .append(" * This class is auto-generated from a properties file.\n")
		  .append(" * Do not version control!\n")
		  .append(" */\n")
		  .append(MessageFormat.format("public final class {0} '{'\n\n", className));
		
		generateNestedClasses(sb, "", config, INDENT);
		sb.append("}\n");
	}

	private void generateNestedClasses(final StringBuilder sb, String root, ConfigObject config, String classIndent) {
		for (Object group : config.keySet()) {
			Object value = config.get(group);
			//generate the class for each group
			generateNestedClass(sb, root, group.toString(), value, classIndent);
		}
	}

	private void generateNestedClass(final StringBuilder sb, String root, String group, Object value, String classIndent) {
		String nextRoot = (root.isEmpty() ? group : root + "." + group);
		if (value instanceof ConfigObject) {
			String nextClassIndent = classIndent + INDENT;
			sb.append(classIndent)
			  .append(MessageFormat.format("public static final class {0} '{'\n\n", group))
			  .append(nextClassIndent)
			  .append(MessageFormat.format("public static final String _ROOT = \"{0}\";\n\n", nextRoot));
			//recursively generate the nested subclasses
			generateNestedClasses(sb, nextRoot, (ConfigObject)value, nextClassIndent);
			sb.append(classIndent)
			  .append("}\n\n");
		}
		else {
			sb.append(classIndent)
			  .append(MessageFormat.format("public static final String {0} = \"{1}\";\n\n", group, nextRoot));
		}
	}

}
