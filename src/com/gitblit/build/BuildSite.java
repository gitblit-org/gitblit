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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
		String linkPattern = "<a href=''{0}''>{1}</a>";
		StringBuilder sb = new StringBuilder();
		for (File file : markdownFiles) {
			String documentName = getDocumentName(file);
			if (!params.skips.contains(documentName)) {
				String displayName = documentName;
				if (aliasMap.containsKey(documentName)) {
					displayName = aliasMap.get(documentName);
				}
				String fileName = documentName + ".html";
				sb.append(MessageFormat.format(linkPattern, fileName, displayName));
				sb.append(" | ");
			}
		}
		sb.setLength(sb.length() - 3);
		sb.trimToSize();

		String htmlHeader = FileUtils.readContent(new File(params.pageHeader), "\n");
		
		String htmlAdSnippet = null;
		if (!StringUtils.isEmpty(params.adSnippet)) {
			File snippet = new File(params.adSnippet);
			if (snippet.exists()) {
				htmlAdSnippet = FileUtils.readContent(snippet, "\n");
			}
		}
		String htmlFooter = FileUtils.readContent(new File(params.pageFooter), "\n");
		String links = sb.toString();
		String header = MessageFormat.format(htmlHeader, Constants.FULL_NAME, links);
		if (!StringUtils.isEmpty(params.analyticsSnippet)) {
			File snippet = new File(params.analyticsSnippet);
			if (snippet.exists()) {
				String htmlSnippet = FileUtils.readContent(snippet, "\n");
				header = header.replace("<!-- ANALYTICS -->", htmlSnippet);
			}
		}		
		final String date = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
		final String footer = MessageFormat.format(htmlFooter, "generated " + date);
		for (File file : markdownFiles) {
			try {
				String documentName = getDocumentName(file);
				if (!params.skips.contains(documentName)) {
					String fileName = documentName + ".html";
					System.out.println(MessageFormat.format("  {0} => {1}", file.getName(),
							fileName));
					InputStreamReader reader = new InputStreamReader(new FileInputStream(file),
							Charset.forName("UTF-8"));
					String content = MarkdownUtils.transformMarkdown(reader);
					for (String token : params.substitutions) {
						String[] kv = token.split("=");
						content = content.replace(kv[0], kv[1]);
					}
					for (String alias : params.loads) {
						String[] kv = alias.split("=");
						String loadedContent = FileUtils.readContent(new File(kv[1]), "\n");
						loadedContent = StringUtils.escapeForHtml(loadedContent, false);
						loadedContent = StringUtils.breakLinesForHtml(loadedContent);
						content = content.replace(kv[0], loadedContent);
					}
					OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(
							new File(destinationFolder, fileName)), Charset.forName("UTF-8"));
					writer.write(header);
					if (!StringUtils.isEmpty(htmlAdSnippet)) {
						writer.write(htmlAdSnippet);
					}
					writer.write(content);
					writer.write(footer);
					reader.close();
					writer.close();
				}
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

	}
}
