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
package com.gitblit.build;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.text.MessageFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import com.gitblit.Constants;
import com.gitblit.utils.FileUtils;
import com.gitblit.utils.MarkdownUtils;
import com.gitblit.utils.StringUtils;

/**
 * Builds the web site or deployment documentation from Markdown source files.
 * 
 * All Markdown source files must have the .mkd extension.
 * 
 * Natural string sort order of the Markdown source filenames is the order of
 * page links. "##_" prefixes are used to control the sort order.
 * 
 * @author James Moger
 * 
 */
public class BuildSite {

	private static final String SPACE_DELIMITED = "SPACE-DELIMITED";

	private static final String CASE_SENSITIVE = "CASE-SENSITIVE";

	private static final String RESTART_REQUIRED = "RESTART REQUIRED";

	private static final String SINCE = "SINCE";

	public static void main(String... args) {
		Params params = new Params();
		JCommander jc = new JCommander(params);
		try {
			jc.parse(args);
		} catch (ParameterException t) {
			usage(jc, t);
		}

		File sourceFolder = new File(params.sourceFolder);
		File destinationFolder = new File(params.outputFolder);
		File[] markdownFiles = sourceFolder.listFiles(new FilenameFilter() {

			@Override
			public boolean accept(File dir, String name) {
				return name.toLowerCase().endsWith(".mkd");
			}
		});
		Arrays.sort(markdownFiles);

		Map<String, String> aliasMap = new HashMap<String, String>();
		for (String alias : params.aliases) {
			String[] values = alias.split("=");
			aliasMap.put(values[0], values[1]);
		}

		System.out.println(MessageFormat.format("Generating site from {0} Markdown Docs in {1} ",
				markdownFiles.length, sourceFolder.getAbsolutePath()));

		String htmlHeader = FileUtils.readContent(new File(params.pageHeader), "\n");

		String htmlAdSnippet = null;
		if (!StringUtils.isEmpty(params.adSnippet)) {
			File snippet = new File(params.adSnippet);
			if (snippet.exists()) {
				htmlAdSnippet = FileUtils.readContent(snippet, "\n");
			}
		}
		String htmlFooter = FileUtils.readContent(new File(params.pageFooter), "\n");
		final String date = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
		final String footer = MessageFormat.format(htmlFooter, "generated " + date);
		for (File file : markdownFiles) {
			String documentName = getDocumentName(file);
			if (params.skips.contains(documentName)) {
				continue;
			}
			try {
				String links = createLinks(file, markdownFiles, aliasMap, params.skips);
				String header = MessageFormat.format(htmlHeader, Constants.FULL_NAME, links);
				if (!StringUtils.isEmpty(params.analyticsSnippet)) {
					File snippet = new File(params.analyticsSnippet);
					if (snippet.exists()) {
						String htmlSnippet = FileUtils.readContent(snippet, "\n");
						header = header.replace("<!-- ANALYTICS -->", htmlSnippet);
					}
				}

				String fileName = documentName + ".html";
				System.out.println(MessageFormat.format("  {0} => {1}", file.getName(), fileName));
				String rawContent = FileUtils.readContent(file, "\n");
				String markdownContent = rawContent;

				Map<String, List<String>> nomarkdownMap = new HashMap<String, List<String>>();

				// extract sections marked as no-markdown
				int nmd = 0;
				for (String token : params.nomarkdown) {
					StringBuilder strippedContent = new StringBuilder();

					String nomarkdownKey = "%NOMARKDOWN" + nmd + "%";
					String[] kv = token.split(":", 2);
					String beginToken = kv[0];
					String endToken = kv[1];

					// strip nomarkdown chunks from markdown and cache them
					List<String> chunks = new Vector<String>();
					int beginCode = 0;
					int endCode = 0;
					while ((beginCode = markdownContent.indexOf(beginToken, endCode)) > -1) {
						if (endCode == 0) {
							strippedContent.append(markdownContent.substring(0, beginCode));
						} else {
							strippedContent.append(markdownContent.substring(endCode, beginCode));
						}
						strippedContent.append(nomarkdownKey);
						endCode = markdownContent.indexOf(endToken, beginCode);
						chunks.add(markdownContent.substring(beginCode, endCode));
						nomarkdownMap.put(nomarkdownKey, chunks);
					}

					// get remainder of text
					if (endCode < markdownContent.length()) {
						strippedContent.append(markdownContent.substring(endCode,
								markdownContent.length()));
					}
					markdownContent = strippedContent.toString();
					nmd++;
				}

				// transform markdown to html
				String content = transformMarkdown(markdownContent.toString());

				// reinsert nomarkdown chunks
				for (Map.Entry<String, List<String>> nomarkdown : nomarkdownMap.entrySet()) {
					for (String chunk : nomarkdown.getValue()) {
						content = content.replaceFirst(nomarkdown.getKey(), chunk);
					}
				}

				for (String token : params.substitutions) {
					String[] kv = token.split("=", 2);
					content = content.replace(kv[0], kv[1]);
				}
				for (String token : params.regex) {
					String[] kv = token.split("!!!", 2);
					content = content.replaceAll(kv[0], kv[1]);
				}
				for (String alias : params.properties) {
					String[] kv = alias.split("=", 2);
					String loadedContent = generatePropertiesContent(new File(kv[1]));
					content = content.replace(kv[0], loadedContent);
				}
				for (String alias : params.loads) {
					String[] kv = alias.split("=", 2);
					String loadedContent = FileUtils.readContent(new File(kv[1]), "\n");
					loadedContent = StringUtils.escapeForHtml(loadedContent, false);
					loadedContent = StringUtils.breakLinesForHtml(loadedContent);
					content = content.replace(kv[0], loadedContent);
				}
				OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(new File(
						destinationFolder, fileName)), Charset.forName("UTF-8"));
				writer.write(header);
				if (!StringUtils.isEmpty(htmlAdSnippet)) {
					writer.write(htmlAdSnippet);
				}
				writer.write(content);
				writer.write(footer);
				writer.close();
			} catch (Throwable t) {
				System.err.println("Failed to transform " + file.getName());
				t.printStackTrace();
			}
		}
	}

	private static String getDocumentName(File file) {
		String displayName = file.getName().substring(0, file.getName().lastIndexOf('.'))
				.toLowerCase();
		int underscore = displayName.indexOf('_') + 1;
		if (underscore > -1) {
			// trim leading ##_ which is to control display order
			return displayName.substring(underscore);
		}
		return displayName;
	}

	private static String createLinks(File currentFile, File[] markdownFiles,
			Map<String, String> aliasMap, List<String> skips) {
		String linkPattern = "<li><a href=''{0}''>{1}</a></li>";
		String currentLinkPattern = "<li class=''active''><a href=''{0}''>{1}</a></li>";
		StringBuilder sb = new StringBuilder();
		for (File file : markdownFiles) {
			String documentName = getDocumentName(file);
			if (!skips.contains(documentName)) {
				String displayName = documentName;
				if (aliasMap.containsKey(documentName)) {
					displayName = aliasMap.get(documentName);
				} else {
					displayName = displayName.replace('_', ' ');
				}
				String fileName = documentName + ".html";
				if (currentFile.getName().equals(file.getName())) {
					sb.append(MessageFormat.format(currentLinkPattern, fileName, displayName));
				} else {
					sb.append(MessageFormat.format(linkPattern, fileName, displayName));
				}
			}
		}
		sb.setLength(sb.length() - 3);
		sb.trimToSize();
		return sb.toString();
	}

	private static String generatePropertiesContent(File propertiesFile) throws Exception {
		// Read the current Gitblit properties
		BufferedReader propertiesReader = new BufferedReader(new FileReader(propertiesFile));

		Vector<Setting> settings = new Vector<Setting>();
		List<String> comments = new ArrayList<String>();
		String line = null;
		while ((line = propertiesReader.readLine()) != null) {
			if (line.length() == 0) {
				Setting s = new Setting("", "", comments);
				settings.add(s);
				comments.clear();
			} else {
				if (line.charAt(0) == '#') {
					comments.add(line.substring(1).trim());
				} else {
					String[] kvp = line.split("=", 2);
					String key = kvp[0].trim();
					Setting s = new Setting(key, kvp[1].trim(), comments);
					settings.add(s);
					comments.clear();
				}
			}
		}
		propertiesReader.close();

		StringBuilder sb = new StringBuilder();
		for (Setting setting : settings) {
			for (String comment : setting.comments) {
				if (comment.contains(SINCE) || comment.contains(RESTART_REQUIRED)
						|| comment.contains(CASE_SENSITIVE) || comment.contains(SPACE_DELIMITED)) {
					sb.append(MessageFormat.format(
							"<span style=\"color:#004000;\"># <i>{0}</i></span>",
							transformMarkdown(comment)));
				} else {
					sb.append(MessageFormat.format("<span style=\"color:#004000;\"># {0}</span>",
							transformMarkdown(comment)));
				}
				sb.append("<br/>\n");
			}
			if (!StringUtils.isEmpty(setting.name)) {
				sb.append(MessageFormat
						.format("<span style=\"color:#000080;\">{0}</span> = <span style=\"color:#800000;\">{1}</span>",
								setting.name, StringUtils.escapeForHtml(setting.value, false)));
			}
			sb.append("<br/>\n");
		}

		return sb.toString();
	}

	private static String transformMarkdown(String comment) throws ParseException {
		String md = MarkdownUtils.transformMarkdown(comment);
		if (md.startsWith("<p>")) {
			md = md.substring(3);
		}
		if (md.endsWith("</p>")) {
			md = md.substring(0, md.length() - 4);
		}
		return md;
	}

	private static void usage(JCommander jc, ParameterException t) {
		System.out.println(Constants.getGitBlitVersion());
		System.out.println();
		if (t != null) {
			System.out.println(t.getMessage());
			System.out.println();
		}
		if (jc != null) {
			jc.usage();
		}
		System.exit(0);
	}

	/**
	 * Setting represents a setting with its comments from the properties file.
	 */
	private static class Setting {
		final String name;
		final String value;
		final List<String> comments;

		Setting(String name, String value, List<String> comments) {
			this.name = name;
			this.value = value;
			this.comments = new ArrayList<String>(comments);
		}
	}

	/**
	 * JCommander Parameters class for BuildSite.
	 */
	@Parameters(separators = " ")
	private static class Params {

		@Parameter(names = { "--sourceFolder" }, description = "Markdown Source Folder", required = true)
		public String sourceFolder;

		@Parameter(names = { "--outputFolder" }, description = "HTML Ouptut Folder", required = true)
		public String outputFolder;

		@Parameter(names = { "--pageHeader" }, description = "Page Header HTML Snippet", required = true)
		public String pageHeader;

		@Parameter(names = { "--pageFooter" }, description = "Page Footer HTML Snippet", required = true)
		public String pageFooter;

		@Parameter(names = { "--adSnippet" }, description = "Ad HTML Snippet", required = false)
		public String adSnippet;

		@Parameter(names = { "--analyticsSnippet" }, description = "Analytics HTML Snippet", required = false)
		public String analyticsSnippet;

		@Parameter(names = { "--skip" }, description = "Filename to skip", required = false)
		public List<String> skips = new ArrayList<String>();

		@Parameter(names = { "--alias" }, description = "Filename=Linkname aliases", required = false)
		public List<String> aliases = new ArrayList<String>();

		@Parameter(names = { "--substitute" }, description = "%TOKEN%=value", required = false)
		public List<String> substitutions = new ArrayList<String>();

		@Parameter(names = { "--load" }, description = "%TOKEN%=filename", required = false)
		public List<String> loads = new ArrayList<String>();

		@Parameter(names = { "--properties" }, description = "%TOKEN%=filename", required = false)
		public List<String> properties = new ArrayList<String>();

		@Parameter(names = { "--nomarkdown" }, description = "%STARTTOKEN%:%ENDTOKEN%", required = false)
		public List<String> nomarkdown = new ArrayList<String>();

		@Parameter(names = { "--regex" }, description = "searchPattern!!!replacePattern", required = false)
		public List<String> regex = new ArrayList<String>();

	}
}
