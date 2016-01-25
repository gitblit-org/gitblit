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
package com.gitblit.wicket.pages;


import java.util.List;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextArea;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.link.ExternalLink;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.model.Model;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import com.gitblit.models.UserModel;
import com.gitblit.servlet.RawServlet;
import com.gitblit.utils.BugtraqProcessor;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.CacheControl;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.CacheControl.LastModified;
import com.gitblit.wicket.MarkupProcessor;
import com.gitblit.wicket.MarkupProcessor.MarkupDocument;
import com.gitblit.wicket.WicketUtils;

@CacheControl(LastModified.BOOT)
public class DocPage extends RepositoryPage {

	public DocPage(final PageParameters params) {
		super(params);

		UserModel currentUser = GitBlitWebSession.get().getUser();
		currentUser = (currentUser == null) ? UserModel.ANONYMOUS : currentUser;
		
		final String path = WicketUtils.getPath(params).replace("%2f", "/").replace("%2F", "/");
		MarkupProcessor processor = new MarkupProcessor(app().settings(), app().xssFilter());

		Repository r = getRepository();
		RevCommit commit = JGitUtils.getCommit(r, objectId);
		String [] encodings = getEncodings();

		// Read raw markup content and transform it to html
		String documentPath = path;
		String markupText = JGitUtils.getStringContent(r, commit.getTree(), path, encodings);

		// Hunt for document
		if (StringUtils.isEmpty(markupText)) {
			String name = StringUtils.stripFileExtension(path);

			List<String> docExtensions = processor.getAllExtensions();
			for (String ext : docExtensions) {
				String checkName = name + "." + ext;
				markupText = JGitUtils.getStringContent(r, commit.getTree(), checkName, encodings);
				if (!StringUtils.isEmpty(markupText)) {
					// found it
					documentPath = path;
					break;
				}
			}
		}

		if (markupText == null) {
			markupText = "";
		}

		BugtraqProcessor bugtraq = new BugtraqProcessor(app().settings());
		markupText = bugtraq.processText(getRepository(), repositoryName, markupText);

		Fragment fragment;
		MarkupDocument markupDoc = processor.parse(repositoryName, getBestCommitId(commit), documentPath, markupText);


		if (currentUser.canEdit(getRepositoryModel())) {
			
			final Model<String> documentContent = new Model<String>(markupDoc.markup);
			final Model<String> commitMessage = new Model<String>("Document update");
					
			fragment = new Fragment("doc", "markupContent", this);
			
			Form<Void> form = new Form<Void>("documentEditor") {
				
				private static final long serialVersionUID = 1L;

				@Override
				protected void onSubmit() {
					System.out.print( commitMessage.getObject() );
					System.out.print( "----------------------" );
					System.out.print( documentContent.getObject() );
					
					//TODO: Perform commit
					//TODO: Set the latest commit hash to redirect to latest version
					//TODO: Error if commit failed, i.e. merge conflict
					setResponsePage(DocPage.class, params);
					return;
				}
			};

			final TextArea<String> docIO = new TextArea<String>("content", documentContent);
			docIO.setOutputMarkupId(false);	
			
			form.add(new Label("commitAuthor", String.format("%s <%s>", currentUser.displayName, currentUser.emailAddress)));
			form.add(new TextArea<String>("commitMessage", commitMessage));
			
			
			form.setOutputMarkupId(false);
			form.add(docIO);

			addBottomScriptInline("function commitChanges() { simplemde.codemirror.save(); document.querySelector('form#documentEditor').submit(); }");
			addBottomScriptInline("var simplemde = attachDocumentEditor(document.querySelector('textarea#editor'), $('#commitDialog'));");
			
	        fragment.add(form);
	        
		} else {
			
			final Model<String> documentContent = new Model<String>(markupDoc.html);
			
			fragment = new Fragment("doc", "plainContent", this);
			
			fragment.add(new Label("content", documentContent).setEscapeModelStrings(false));
		}

		// document page links
		fragment.add(new BookmarkablePageLink<Void>("blameLink", BlamePage.class,
				WicketUtils.newPathParameter(repositoryName, objectId, documentPath)));
		fragment.add(new BookmarkablePageLink<Void>("historyLink", HistoryPage.class,
				WicketUtils.newPathParameter(repositoryName, objectId, documentPath)));
		String rawUrl = RawServlet.asLink(getContextUrl(), repositoryName, objectId, documentPath);
		fragment.add(new ExternalLink("rawLink", rawUrl));

		add(fragment);
        
	}

	@Override
	protected String getPageName() {
		return getString("gb.docs");
	}

	@Override
	protected boolean isCommitPage() {
		return true;
	}

	@Override
	protected Class<? extends BasePage> getRepoNavPageClass() {
		return DocsPage.class;
	}
}
