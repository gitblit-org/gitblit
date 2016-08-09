/*
 * Copyright 2012 gitblit.com.
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

import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;

import org.apache.wicket.Component;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.markup.ComponentTag;
import org.apache.wicket.markup.MarkupStream;
import org.apache.wicket.markup.html.form.StatelessForm;
import org.apache.wicket.util.encoding.UrlDecoder;
import org.apache.wicket.util.string.AppendingStringBuffer;
import org.apache.wicket.util.string.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.Constants;
import com.gitblit.utils.GitBlitRequestUtils;
import com.gitblit.wicket.pages.BasePage;

/**
 * This class is used to create a stateless form that can POST or GET to a
 * bookmarkable page regardless of the pagemap and even after session expiration
 * or a server restart.
 *
 * The trick is to embed "wicket:bookmarkablePage" as a hidden field of the form.
 * Wicket already has logic to extract this parameter when it is trying
 * to determine which page should receive the request.
 *
 * The parameters of the containing page can optionally be included as hidden
 * fields in this form.  Note that if a page parameter's name collides with any
 * child's wicket:id in this form then the page parameter is excluded.
 *
 * @author James Moger
 *
 */
public class SessionlessForm<T> extends StatelessForm<T> {

	private static final long serialVersionUID = 1L;

	private static final String HIDDEN_DIV_START = "<div style=\"width:0px;height:0px;position:absolute;left:-100px;top:-100px;overflow:hidden\">";

	protected final Class<? extends BasePage> pageClass;

	protected final PageParameters pageParameters;

	private final Logger log = LoggerFactory.getLogger(SessionlessForm.class);

	/**
	 * Sessionless forms must have a bookmarkable page class.  A bookmarkable
	 * page is defined as a page that has only a default and/or a PageParameter
	 * constructor.
	 *
	 * @param id
	 * @param bookmarkablePageClass
	 */
	public SessionlessForm(String id, Class<? extends BasePage> bookmarkablePageClass) {
		this(id, bookmarkablePageClass, null);
	}

	/**
	 * Sessionless forms must have a bookmarkable page class.  A bookmarkable
	 * page is defined as a page that has only a default and/or a PageParameter
	 * constructor.
	 *
	 * @param id
	 * @param bookmarkablePageClass
	 * @param pageParameters
	 */
	public SessionlessForm(String id, Class<? extends BasePage> bookmarkablePageClass,
			PageParameters pageParameters) {
		super(id);
		this.pageClass = bookmarkablePageClass;
		this.pageParameters = pageParameters;
	}


	/**
	 * Append an additional hidden input tag that forces Wicket to correctly
	 * determine the destination page class even after a session expiration or
	 * a server restart.
	 *
	 * @param markupStream
	 *            The markup stream
	 * @param openTag
	 *            The open tag for the body
	 */
	@Override
	public void onComponentTagBody(final MarkupStream markupStream, final ComponentTag openTag)
	{
		// render the hidden bookmarkable page field
		AppendingStringBuffer buffer = new AppendingStringBuffer(HIDDEN_DIV_START);
		buffer.append("<input type=\"hidden\" name=\"")
			.append("wicket:bookmarkablePage")
			.append("\" value=\":")
			.append(pageClass.getName())
			.append("\" />");

		// insert the page parameters, if any, as hidden fields as long as they
		// do not collide with any child wicket:id of the form.
		if (pageParameters != null) {
			for (String key : pageParameters.getNamedKeys()) {
				Component c = get(key);
				if (c != null) {
					// this form has a field id which matches the
					// parameter name, skip embedding a hidden value
					log.warn(MessageFormat.format("Skipping page parameter \"{0}\" from sessionless form hidden fields because it collides with a form child wicket:id", key));
					continue;
				}
				String value = pageParameters.get(key).toString();
				buffer.append("<input type=\"hidden\" name=\"")
				.append(recode(key))
				.append("\" value=\"")
				.append(recode(value))
				.append("\" />");
			}
		}

		buffer.append("</div>");
		getResponse().write(buffer);
		super.onComponentTagBody(markupStream, openTag);
	}

	/**
	 * Take URL-encoded query string value, unencode it and return HTML-escaped version
	 *
	 * @param s
	 *            value to reencode
	 * @return reencoded value
	 */
	private String recode(String s) {
		String un = UrlDecoder.QUERY_INSTANCE.decode(s, Constants.ENCODING);
		return Strings.escapeMarkup(un).toString();
	}

	protected String getAbsoluteUrl() {
		return getAbsoluteUrl(pageClass, pageParameters);
	}

	protected String getAbsoluteUrl(Class<? extends BasePage> pageClass, PageParameters pageParameters) {
		return GitBlitRequestUtils.toAbsoluteUrl(pageClass, pageParameters);
	}
}
