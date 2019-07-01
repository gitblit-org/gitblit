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

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple highlighter for the html display of a properties file,
 * via a %PROPERTIES% token replacement.
 */
public class PropertiesSyntaxHighlighter extends DefaultTask {

	private static Logger logger = LoggerFactory.getLogger(PropertiesSyntaxHighlighter.class.getName());
	
	private static String newLine = System.lineSeparator();

	private static String beginRemarkSpan = "<span style='color:#004000;'>"; 
	private static String endRemarkSpan = "</span><br/>" + newLine; 
	private static String beginKeySpan = "<span style='color:#000080;'>"; 
	private static String endKeySpan = "</span>"; 
	private static String beginValueSpan = "<span style='color:#800000;'>"; 
	private static String endValueSpan = "</span><br/>" + newLine; 

	File propertiesFile;
	File outputFile;
	
	@InputFile
	public File getPropertiesFile() {
		return propertiesFile;
	}

	@OutputFile
	public File getOutputFile() {
		return outputFile;
	}

	public void setPropertiesFile(File propertiesFile) {
		this.propertiesFile = propertiesFile;
	}

	public void setOutputFile(File outputFile) {
		this.outputFile = outputFile;
	}

	@TaskAction
	public void generateHtml() throws IOException {
		logger.info("PropertiesSyntaxHighlighter");
		logger.info("    using properties file: " + propertiesFile.getCanonicalPath());
		logger.info("    to replace %PROPERTIES% token in file: " + outputFile.getCanonicalPath());
		String outputText = null;
		try (Stream<String> linesStream = Files.lines(outputFile.toPath(), StandardCharsets.UTF_8)) {
			outputText = linesStream.collect(Collectors.joining(newLine));
		}
		StringBuilder sb = new StringBuilder();
		try (Stream<String> linesStream = Files.lines(propertiesFile.toPath(), StandardCharsets.UTF_8)) {
			linesStream.forEach(line -> {
				line = line.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
				if (line.trim().isEmpty()) {
					sb.append("<br/>").append(newLine);
				}
				else if (line.trim().startsWith("#")) {
					sb.append(beginRemarkSpan).append(line).append(endRemarkSpan);
				}
				else {
					int firstEqualIndex = line.indexOf("=");
					String key = line.substring(0, firstEqualIndex);
					String value = line.substring(firstEqualIndex + 1);
					sb.append(beginKeySpan).append(key.trim()).append(endKeySpan).append(" = ");
					if (value.trim().isEmpty()) {
						sb.append("<br/>").append(newLine);
					}
					else {
						sb.append(beginValueSpan).append(value.trim()).append(endValueSpan);
					}
				}
			});
        }

		try (BufferedWriter bw = Files.newBufferedWriter(outputFile.toPath(), StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING)) {
			bw.write(outputText.replace("%PROPERTIES%", sb.toString()));
		}
	}

}
