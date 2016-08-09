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
package com.gitblit.wicket.panels;

import org.apache.wicket.ajax.AbstractAjaxTimerBehavior;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.TextArea;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.util.time.Duration;

import com.gitblit.utils.MarkdownUtils;
import com.gitblit.wicket.GitBlitWebApp;

public class MarkdownTextArea extends TextArea {

	private static final long serialVersionUID = 1L;

	protected String repositoryName;

	protected String text = "";

	public MarkdownTextArea(String id, final IModel<String> previewModel, final Label previewLabel) {
		super(id);
		setModel(new PropertyModel(this, "text"));
		add(new AjaxFormComponentUpdatingBehavior("onblur") {
			private static final long serialVersionUID = 1L;

			@Override
			protected void onUpdate(AjaxRequestTarget target) {
				renderPreview(previewModel);
				if (target != null) {
					target.add(previewLabel);
				}
			}
		});
		add(new AjaxFormComponentUpdatingBehavior("onchange") {
			private static final long serialVersionUID = 1L;

			@Override
			protected void onUpdate(AjaxRequestTarget target) {
				renderPreview(previewModel);
				if (target != null) {
					target.add(previewLabel);
				}
			}
		});

		add(new KeepAliveBehavior());
		setOutputMarkupId(true);
	}

	protected void renderPreview(IModel<String> previewModel) {
		if (text == null) {
			return;
		}
		String html = MarkdownUtils.transformGFM(GitBlitWebApp.get().settings(), text, repositoryName);
		String safeHtml = GitBlitWebApp.get().xssFilter().relaxed(html);
		previewModel.setObject(safeHtml);
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}

	public void setRepository(String repositoryName) {
		this.repositoryName = repositoryName;
	}

//	@Override
//	protected void onBeforeRender() {
//		super.onBeforeRender();
//		add(new RichTextSetActiveTextFieldAttributeModifier(this.getMarkupId()));
//	}
//
//	private class RichTextSetActiveTextFieldAttributeModifier extends AttributeModifier {
//
//		private static final long serialVersionUID = 1L;
//
//		public RichTextSetActiveTextFieldAttributeModifier(String markupId) {
//			super("onClick", true, new Model("richTextSetActiveTextField('" + markupId + "');"));
//		}
//	}

	private class KeepAliveBehavior extends AbstractAjaxTimerBehavior {

		private static final long serialVersionUID = 1L;

		public KeepAliveBehavior() {
			super(Duration.minutes(5));
		}

		@Override
		protected void onTimer(AjaxRequestTarget target) {
			// prevent wicket changing focus
			target.focusComponent(null);
		}
	}
}