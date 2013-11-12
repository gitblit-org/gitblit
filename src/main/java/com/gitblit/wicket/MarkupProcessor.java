/*
 * Copyright 2013 gitblit.com.
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
package com.gitblit.wicket;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import org.apache.wicket.Page;
import org.apache.wicket.RequestCycle;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.pegdown.LinkRenderer;
import org.pegdown.ast.WikiLinkNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.models.PathModel;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.MarkdownUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.pages.DocPage;

/**
 * Processes markup content and generates html with repository-relative page and
 * image linking.
 *
 * @author James Moger
 *
 */
public class MarkupProcessor {

	public enum MarkupSyntax {
		PLAIN, MARKDOWN
	}

	private Logger logger = LoggerFactory.getLogger(getClass());

	private final IStoredSettings settings;

	public MarkupProcessor(IStoredSettings settings) {
		this.settings = settings;
	}

	public List<String> getMarkupExtensions() {
		List<String> list = new ArrayList<String>();
		list.addAll(settings.getStrings(Keys.web.markdownExtensions));
		return list;
	}

	private MarkupSyntax determineSyntax(String documentPath) {
		String ext = StringUtils.getFileExtension(documentPath).toLowerCase();
		if (StringUtils.isEmpty(ext)) {
			return MarkupSyntax.PLAIN;
		}

		if (settings.getStrings(Keys.web.markdownExtensions).contains(ext)) {
			return MarkupSyntax.MARKDOWN;
		}

		return MarkupSyntax.PLAIN;
	}

	public MarkupDocument parseReadme(Repository r, String repositoryName, String commitId) {
		String readme = null;
		RevCommit commit = JGitUtils.getCommit(r, commitId);
		List<PathModel> paths = JGitUtils.getFilesInPath(r, null, commit);
		for (PathModel path : paths) {
			if (!path.isTree()) {
				String name = path.name.toLowerCase();
				if (name.equals("readme") || name.equals("readme.txt")) {
					readme = path.name;
					break;
				} else if (name.startsWith("readme.")) {
					String ext = StringUtils.getFileExtension(name).toLowerCase();
					if (getMarkupExtensions().contains(ext)) {
						readme = path.name;
						break;
					}
				}
			}
		}

		if (!StringUtils.isEmpty(readme)) {
			String [] encodings = settings.getStrings(Keys.web.blobEncodings).toArray(new String[0]);
			String markup = JGitUtils.getStringContent(r, commit.getTree(), readme, encodings);
			return parse(repositoryName, commitId, readme, markup);
		}

		return null;
	}

	public MarkupDocument parse(String repositoryName, String commitId, String documentPath, String markupText) {
		final MarkupSyntax syntax = determineSyntax(documentPath);
		final MarkupDocument doc = new MarkupDocument(documentPath, markupText, syntax);

		if (markupText != null) {
			try {
				switch (syntax){
				case MARKDOWN:
					parse(doc, repositoryName, commitId);
					break;
				default:
					doc.html = MarkdownUtils.transformPlainText(markupText);
					break;
				}
			} catch (Exception e) {
				logger.error("failed to transform " + syntax, e);
			}
		}

		if (doc.html == null) {
			// failed to transform markup
			if (markupText == null) {
				markupText = String.format("Document <b>%1$s</b> not found in <em>%2$s</em>", documentPath, repositoryName);
			}
			markupText = MessageFormat.format("<div class=\"alert alert-error\"><strong>{0}:</strong> {1}</div>{2}", "Error", "failed to parse markup", markupText);
			doc.html = StringUtils.breakLinesForHtml(markupText);
		}

		return doc;
	}

	/**
	 * Parses the document as Markdown using Pegdown.
	 *
	 * @param doc
	 * @param repositoryName
	 * @param commitId
	 */
	private void parse(final MarkupDocument doc, final String repositoryName, final String commitId) {
		LinkRenderer renderer = new LinkRenderer() {
			@Override
			public Rendering render(WikiLinkNode node) {
				String path = doc.getRelativePath(node.getText());
				String name = getDocumentName(path);
				String url = getWicketUrl(DocPage.class, repositoryName, commitId, path);
				return new Rendering(url, name);
			}
		};
		doc.html = MarkdownUtils.transformMarkdown(doc.markup, renderer);
	}

	private String getWicketUrl(Class<? extends Page> pageClass, final String repositoryName, final String commitId, final String document) {
		String fsc = settings.getString(Keys.web.forwardSlashCharacter, "/");
		String encodedPath = document.replace(' ', '-');
		try {
			encodedPath = URLEncoder.encode(encodedPath, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			logger.error(null, e);
		}
		encodedPath = encodedPath.replace("/", fsc).replace("%2F", fsc);

		String url = RequestCycle.get().urlFor(pageClass, WicketUtils.newPathParameter(repositoryName, commitId, encodedPath)).toString();
		return url;
	}

	private String getDocumentName(final String document) {
		// extract document name
		String name = StringUtils.stripFileExtension(document);
		name = name.replace('_', ' ');
		if (name.indexOf('/') > -1) {
			name = name.substring(name.lastIndexOf('/') + 1);
		}
		return name;
	}

	public static class MarkupDocument implements Serializable {

		private static final long serialVersionUID = 1L;

		public final String documentPath;
		public final String markup;
		public final MarkupSyntax syntax;
		public String html;

		MarkupDocument(String documentPath, String markup, MarkupSyntax syntax) {
			this.documentPath = documentPath;
			this.markup = markup;
			this.syntax = syntax;
		}

		String getCurrentPath() {
			String basePath = "";
			if (documentPath.indexOf('/') > -1) {
				basePath = documentPath.substring(0, documentPath.lastIndexOf('/') + 1);
				if (basePath.charAt(0) == '/') {
					return basePath.substring(1);
				}
			}
			return basePath;
		}

		String getRelativePath(String ref) {
			return ref.charAt(0) == '/' ? ref.substring(1) : (getCurrentPath() + ref);
		}
	}
}
