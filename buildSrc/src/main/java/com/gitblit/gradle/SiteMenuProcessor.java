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
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.moxie.maxml.Maxml;
import org.moxie.maxml.MaxmlMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import freemarker.template.Configuration;
import freemarker.template.DefaultObjectWrapper;
import freemarker.template.Version;
import groovy.util.Node;
import groovy.util.XmlParser;

/**
 * Gradle task to parse the menu-structure.xml file and to
 * produce the menu header and footer for all document pages.
 * It also produces the html files from some Freemarker templates
 * applied to a given input data file of Moxie-specific format.
 */
public class SiteMenuProcessor extends DefaultTask {

	private static Logger logger = LoggerFactory.getLogger(SiteMenuProcessor.class.getName());

	private static final String newLine = System.lineSeparator();

	private static final String HEADER_START =
			"<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\" \"http://www.w3.org/TR/html4/loose.dtd\">" + newLine + 
			"<html>" + newLine + 
			"<head>" + newLine + 
			"	<!-- Begin Header -->" + newLine + 
			"	<title>${project.name}</title>" + newLine + 
			"	<meta charset='utf-8'>" + newLine + 
			"	<meta name='ROBOTS' content='INDEX'>" + newLine + 
			"	<meta http-equiv='Content-Type' content='text/html; charset=UTF-8'>" + newLine + 
			"	<meta name='viewport' content='width=device-width, initial-scale=1.0'>" + newLine + 
			"	<link rel='stylesheet' href='./bootstrap/css/bootstrap.css' />" + newLine + 
			"	<link rel='shortcut icon' type='image/png' href='./gitblt-favicon.png' />" + newLine + 
			"	<link rel='stylesheet' href='./prettify/prettify.css' />" + newLine + 
			"	<script src='./prettify/prettify.js'></script>" + newLine + 
			"	<script src='./bootstrap/js/jquery.js'></script>" + newLine + 
			"	<script src='./bootstrap/js/bootstrap.min.js'></script>" + newLine + 
			"</head>" + newLine + 
			"<body onload='prettyPrint()'>" + newLine + 
			"<div class='navbar navbar-fixed-top'>	<!-- Navigation Bar -->" + newLine + 
			"	<div class='navbar-inner'>" + newLine + 
			"		<div class='container'>" + newLine + 
			"			<a class='btn btn-navbar' data-toggle='collapse' data-target='.nav-collapse'>" + newLine + 
			"				<span class='icon-bar'></span>" + newLine + 
			"				<span class='icon-bar'></span>" + newLine + 
			"				<span class='icon-bar'></span>" + newLine + 
			"			</a>" + newLine + 
			"			<a class='brand' href='./'><img src='./gitblt_25_white.png' alt='Gitblit'></a>" + newLine + 
			"			<div class='nav-collapse'>" + newLine + 
			"				<ul class='nav'>" + newLine;

	private static final String HEADER_END = 
			"				</ul><!--/.nav -->" + newLine + 
			"			</div><!--/.nav-collapse -->" + newLine + 
			"		</div><!--/.container -->" + newLine + 
			"	</div><!--/.navbar-inner -->" + newLine + 
			"</div><!-- end Navigation Bar -->" + newLine + 
			"<div class='container'>" + newLine + 
			"<!-- Begin Markdown -->" + newLine;

	private static final String FOOTER = 
			"	<footer class='footer'><p class='pull-right'>generated ${project.releaseDate}</p>" + newLine +
			"		<p>The content of this page is licensed under the <a href='http://creativecommons.org/licenses/by/3.0'>Creative Commons Attribution 3.0 License</a>.</p>" + newLine +
			"	</footer>" + newLine +
			"</div><!--/.container -->" + newLine +
			"</body>" + newLine +
			"</html>" + newLine;

	File menuStructureFile;
	File markdownDir;
	String freeMarkerVersion;
	File releaseLogFile;
	File templatesDir;
	File outputDir;
	Map<String, Object> templateSubstitutions;

	@InputDirectory
	public File getMarkdownDir() {
		return markdownDir;
	}

	@InputFile
	public File getMenuStructureFile() {
		return menuStructureFile;
	}

	@Input
	public String getFreeMarkerVersion() {
		return freeMarkerVersion;
	}

	@InputFile
	public File getReleaseLogFile() {
		return releaseLogFile;
	}

	@InputDirectory
	public File getTemplatesDir() {
		return templatesDir;
	}

	@Input
	public Map<String, Object> getTemplateSubstitutions() {
		return templateSubstitutions;
	}

	@OutputDirectory
	public File getOutputDir() {
		return outputDir;
	}

	public void setMenuStructureFile(File menuStructureFile) {
		this.menuStructureFile = menuStructureFile;
	}

	public void setMarkdownDir(File markdownDir) {
		this.markdownDir = markdownDir;
	}

	public void setFreeMarkerVersion(String freeMarkerVersion) {
		this.freeMarkerVersion = freeMarkerVersion;
	}

	public void setReleaseLogFile(File releaseLogFile) {
		this.releaseLogFile = releaseLogFile;
	}

	public void setTemplatesDir(File templatesDir) {
		this.templatesDir = templatesDir;
	}

	public void setTemplateSubstitutions(Map<String, Object> templateSubstitutions) {
		this.templateSubstitutions = templateSubstitutions;
	}

	public void setOutputDir(File outputDir) {
		this.outputDir = outputDir;
	}

	@TaskAction
	public void processSiteMenuStructure() throws Exception {
		logger.info("SiteMenuProcessor: processing '" + menuStructureFile.getName() + "'...");
		Node rootNode = new XmlParser().parse(menuStructureFile);
		StringBuilder pageHeader = new StringBuilder(HEADER_START);
		//recursively process all children of the root node
		processChildren(pageHeader, rootNode, null);
		pageHeader.append(HEADER_END);

		//save the generated page header and footer
		File outFile = new File(outputDir, "pageHeader.html");
		try (BufferedWriter bw = Files.newBufferedWriter(outFile.toPath(), StandardCharsets.UTF_8)) {
			bw.write(pageHeader.toString());
		}
		outFile = new File(outputDir, "pageFooter.html");
		try (BufferedWriter bw = Files.newBufferedWriter(outFile.toPath(), StandardCharsets.UTF_8)) {
			bw.write(FOOTER);
		}
	}

	private void processChildren(StringBuilder sb, Node parent, Map<?,?> menuAttributes)
			throws Exception {
		List<Map<String, String>> pageGroup = new ArrayList<>();
		for (Object child : parent.children()) {
			if (child instanceof Node) {
				Node childNode = (Node) child;
				switch (childNode.name().toString()) {
				case "menu":
					if (menuAttributes == null) {
						processMenu(sb, childNode);
					}
					else {
						processSubMenu(sb, childNode);
					}
					break;
				case "page": 
					Map<String, String> pageAttributes = processPage(sb, childNode);
					if (pageAttributes != null) {
						pageGroup.add(pageAttributes);
					}
					break;
				case "divider":
					if (!pageGroup.isEmpty()) {
						copyPages(pageGroup, menuAttributes);
						pageGroup = new ArrayList<>();
					}
					processDivider(sb, childNode); 
					break;
				case "link":
					processLink(sb, childNode);
					break;
				case "template":
					break;
				default:
					throw new IllegalStateException("Unknown element '" + childNode.name() + "'");
				}
			}
			else {
				System.err.println("Unknown child class: " + child.getClass());
			}
		}
		if (!pageGroup.isEmpty()) {
			copyPages(pageGroup, menuAttributes);
		}
	}
	
	private void processMenu(StringBuilder sb, Node menu) throws Exception {
		sb.append("<li class='dropdown'> <!-- Menu -->").append(newLine)
		  .append("<a class='dropdown-toggle' href='#' data-toggle='dropdown'>")
		  .append(menu.attribute("name"))
		  .append("<b class='caret'></b></a>").append(newLine)
		  .append("<ul class='dropdown-menu'>").append(newLine);
		//recursively process all children of this menu node
		processChildren(sb, menu, menu.attributes());
		sb.append("</ul></li> <!-- End Menu -->").append(newLine);
	}

	private void processSubMenu(StringBuilder sb, Node menu) throws Exception {
		sb.append("<li class='dropdown-submenu'> <!-- SubMenu -->").append(newLine)
		  .append("<a tabindex='-1' href='#'>")
		  .append(menu.attribute("name"))
		  .append("</a>").append(newLine)
		  .append("<ul class='dropdown-menu'>").append(newLine);
		//recursively process all children of this submenu node
		processChildren(sb, menu, menu.attributes());
		sb.append("</ul></li> <!-- End SubMenu -->").append(newLine);
	}

	private Map<String, String> processPage(StringBuilder sb, Node page)
			throws Exception {
		Object name = page.attribute("name");
		Object src = page.attribute("src");
		Object out = page.attribute("out");
		if (out == null) {
			out = ((String)src).replace(".mkd", ".html");
		}
		else if (src == null) {
			//'out' is specified, but 'src' is not;
			//more info must be from a template in a child element
			Node template = (Node) page.children().get(0);
			if (!"template".equals(template.name())) {
				throw new IllegalStateException("Unknown child node: " + template);
			}
			Object templateSrc = template.attribute("src");
			//Object data = template.attribute("data");
			//data = releaseLogFile.getName();
			generateReleaseHtmlDoc(releaseLogFile, (String)templateSrc, (String)out);
		}

		sb.append("<li><a href='").append(out).append("'>")
		  .append(name)
		  .append("</a></li>").append(newLine);

		if (src == null) {
			return null;
		}
		else {
			Map<String, String> pageAttributes = new HashMap<>();
			pageAttributes.put("name", name.toString());
			pageAttributes.put("src", src.toString());
			pageAttributes.put("out", out.toString());
			return pageAttributes;
		}
	}

	private void processLink(StringBuilder sb, Node link) {
		sb.append("<li><a href='").append(link.attribute("src")).append("'>")
		  .append(link.attribute("name"))
		  .append("</a></li>").append(newLine);
	}

	private void processDivider(StringBuilder sb, Node divider) {
		sb.append("<li class='divider'></li>").append(newLine);
	}

	private void copyPages(List<Map<String, String>> pages, Map<?,?> menuAttributes)
			throws IOException {
		boolean pagerNeeded = false;
		if (menuAttributes != null) {
			Object pager = menuAttributes.get("pager");
			if (pager != null) {
				pagerNeeded = Boolean.parseBoolean(pager.toString());
			}
		}

		StringBuilder sb = null;
		Map<String, String> currentPage = null, previousPage = null;
		for (Map<String, String> page : pages) {
			String name = page.get("name");
			String src = page.get("src");
			String out = page.get("out");
			if (pagerNeeded) {
				if (currentPage == null) {
					//page 1
					//nothing to do here
				}
				else if (previousPage == null) {
					//page 2
					addNextPager(sb, name, out);
					writeMkdFileToOutputDir(sb, currentPage.get("out"));
					previousPage = currentPage;
				}
				else {
					//page 3+
					addBothPagers(sb, previousPage, page);
					writeMkdFileToOutputDir(sb, currentPage.get("out"));
					previousPage = currentPage;
				}
				sb = readMkdFile(src);
				currentPage = page;
			}
			else {
				copyMkdFileToOutputDir(src, out);
			}
		}
		if (pagerNeeded) {
			if (previousPage != null) {
				addPreviousPager(sb, previousPage.get("name"), previousPage.get("out"));
			}
			writeMkdFileToOutputDir(sb, currentPage.get("out"));
		}
	}

	private StringBuilder readMkdFile(String src)
			throws IOException {
		StringBuilder sb = new StringBuilder();
		File mkdFile = new File(markdownDir, src);
		try (Stream<String> linesStream = Files.lines(mkdFile.toPath(), StandardCharsets.UTF_8)) {
			sb.append(linesStream.collect(Collectors.joining(newLine)));
		}
		return sb;
	}

	private void writeMkdFileToOutputDir(StringBuilder sb, String out)
			throws IOException {
		File outFile = new File(outputDir, out.replace(".html", ".mkd"));
		try (BufferedWriter bw = Files.newBufferedWriter(outFile.toPath(), StandardCharsets.UTF_8)) {
			bw.write(sb.toString());
		}
	}

	private void copyMkdFileToOutputDir(String src, String out)
			throws IOException {
		StringBuilder sb = readMkdFile(src);
		sb.append(newLine)
		  .append(newLine)
		  .append("<!-- End Markdown -->").append(newLine);
		writeMkdFileToOutputDir(sb, out);
	}

	private void addNextPager(StringBuilder sb, String name, String out) {
		sb.append(newLine)
		  .append(newLine)
		  .append("<!-- End Markdown -->").append(newLine) 
		  .append("<div><ul class='pager'><li class='next'><a href='")
		  .append(out).append("'>")
		  .append(name)
		  .append(" &rarr;</a></li></ul></div>").append(newLine);
	}

	private void addPreviousPager(StringBuilder sb, String name, String out) {
		sb.append(newLine)
		  .append(newLine)
		  .append("<!-- End Markdown -->").append(newLine) 
		  .append("<div><ul class='pager'><li class='previous'><a href='")
		  .append(out).append("'>&larr; ")
		  .append(name)
		  .append("</a></li></ul></div>").append(newLine);
	}

	private void addBothPagers(StringBuilder sb, Map<String, String> previousPage, Map<String, String> nextPage) {
		sb.append(newLine)
		  .append(newLine)
		  .append("<!-- End Markdown -->").append(newLine) 
		  .append("<div><ul class='pager'><li class='previous'><a href='")
		  .append(previousPage.get("out")).append("'>&larr; ")
		  .append(previousPage.get("name"))
		  .append("</a></li>")
		  .append("<li class='next'><a href='")
		  .append(nextPage.get("out")).append("'>")
		  .append(nextPage.get("name"))
		  .append(" &rarr;</a></li></ul></div>").append(newLine);
	}

	/**
	 * Generate html file from a Moxie-specific input data file,
	 * based on the provided Freemarker template.
	 * Moxie class org.moxie.maxml.Maxml is used to parse the Moxie-specific data file.
	 * Main code snippet was taken from org.moxie.Docs.processTemplates() method
	 * to parse the data file and process it with the given Freemarker template.
	 * 
	 * @param dataFile    the Moxie-specific data file to parse
	 * @param templateSrc the Freemarker template to use
	 * @param out         the output file name
	 * 
	 * @throws Exception
	 */
	private void generateReleaseHtmlDoc(File dataFile, String templateSrc, String out)
			throws Exception {
		logger.info("SiteMenuProcessor (using Freemarker version: " + freeMarkerVersion + ")");
		//set up the Freemarker engine
		Version fmVersion = new Version(freeMarkerVersion);
		Configuration fm = new Configuration(fmVersion);
		fm.setObjectWrapper(new DefaultObjectWrapper(fmVersion));
		fm.setDirectoryForTemplateLoading(templatesDir);
		
		logger.info("    templatesDir: " + templatesDir.getCanonicalPath());
		logger.info("    input data file: " + dataFile.getCanonicalPath());

		// populate map with build properties by splitting them into maps
		MaxmlMap dataMap = Maxml.parse(dataFile);
		for (Entry<String, ?> entry : templateSubstitutions.entrySet()) {
			String prop = entry.getKey();
			Object value = entry.getValue();
			MaxmlMap keyMap = dataMap;
			// recursively create/find the destination map
			while (prop.indexOf('.') > -1) {
				String m = prop.substring(0, prop.indexOf('.'));
				if (!keyMap.containsKey(m)) {
					keyMap.put(m, new MaxmlMap());
				}
				keyMap = keyMap.getMap(m);
				prop = prop.substring(m.length() + 1);
			}
			
			// inject property into map
			keyMap.put(prop, value);
		}

		//load and process the Freemarker template
		freemarker.template.Template ftl = fm.getTemplate(templateSrc);
		File outFile = new File(outputDir, out);
		StringWriter writer = new StringWriter();
		ftl.process(dataMap, writer);
		try (BufferedWriter bw = Files.newBufferedWriter(outFile.toPath(), StandardCharsets.UTF_8)) {
			bw.write(writer.toString());
		}
		logger.info("    template: " + templateSrc + "\n    output: " + outFile.getCanonicalPath());
	}

}
