/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gitblit.wicket.freemarker;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;

import org.apache.wicket.MarkupContainer;
import org.apache.wicket.WicketRuntimeException;
import org.apache.wicket.markup.ComponentTag;
import org.apache.wicket.markup.IMarkupCacheKeyProvider;
import org.apache.wicket.markup.IMarkupResourceStreamProvider;
import org.apache.wicket.markup.MarkupStream;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.util.resource.IResourceStream;
import org.apache.wicket.util.resource.StringResourceStream;
import org.apache.wicket.util.string.Strings;

import com.gitblit.utils.StringUtils;

import freemarker.template.Template;
import freemarker.template.TemplateException;

/**
 * This class allows FreeMarker to be used as a Wicket preprocessor or as a
 * snippet injector for something like a CMS.  There are some cases where Wicket
 * is not flexible enough to generate content, especially when you need to generate
 * hybrid HTML/JS content outside the scope of Wicket.
 *
 * @author James Moger
 *
 */
@SuppressWarnings("unchecked")
public class FreemarkerPanel extends Panel
		implements
			IMarkupResourceStreamProvider,
			IMarkupCacheKeyProvider
{
	private static final long serialVersionUID = 1L;

	private final String template;
	private boolean parseGeneratedMarkup;
	private boolean escapeHtml;
	private boolean throwFreemarkerExceptions;
	private transient String stackTraceAsString;
	private transient String evaluatedTemplate;


	/**
	 * Construct.
	 *
	 * @param id
	 *            Component id
	 * @param template
	 *            The Freemarker template
	 * @param values
	 *            values map that can be substituted by Freemarker.
	 */
	public FreemarkerPanel(final String id, String template, final Map<String, Object> values)
	{
		this(id, template, Model.ofMap(values));
	}

	/**
	 * Construct.
	 *
	 * @param id
	 *            Component id
	 * @param templateResource
	 *            The Freemarker template as a string resource
	 * @param model
	 *            Model with variables that can be substituted by Freemarker.
	 */
	public FreemarkerPanel(final String id, final String template, final IModel< ? extends Map<String, Object>> model)
	{
		super(id, model);
		this.template = template;
	}

	/**
	 * Gets the Freemarker template.
	 *
	 * @return the Freemarker template
	 */
	private Template getTemplate()
	{
		if (StringUtils.isEmpty(template))
		{
			throw new IllegalArgumentException("Template not specified!");
		}

		try {
			return Freemarker.getTemplate(template);
		} catch (IOException e) {
			onException(e);
		}

		return null;
	}

	/**
	 * @see org.apache.wicket.markup.html.panel.Panel#onComponentTagBody(org.apache.wicket.markup.
	 *      MarkupStream, org.apache.wicket.markup.ComponentTag)
	 */
	@Override
	public void onComponentTagBody(MarkupStream markupStream, ComponentTag openTag)
	{
		if (!Strings.isEmpty(stackTraceAsString))
		{
			// TODO: only display the Freemarker error/stacktrace in development
			// mode?
			replaceComponentTagBody(markupStream, openTag, Strings
					.toMultilineMarkup(stackTraceAsString));
		}
		else if (!parseGeneratedMarkup)
		{
			// check that no components have been added in case the generated
			// markup should not be
			// parsed
			if (size() > 0)
			{
				throw new WicketRuntimeException(
						"Components cannot be added if the generated markup should not be parsed.");
			}

			if (evaluatedTemplate == null)
			{
				// initialize evaluatedTemplate
				getMarkupResourceStream(null, null);
			}
			replaceComponentTagBody(markupStream, openTag, evaluatedTemplate);
		}
		else
		{
			super.onComponentTagBody(markupStream, openTag);
		}
	}

	/**
	 * Either print or rethrow the throwable.
	 *
	 * @param exception
	 *            the cause
	 * @param markupStream
	 *            the markup stream
	 * @param openTag
	 *            the open tag
	 */
	private void onException(final Exception exception)
	{
		if (!throwFreemarkerExceptions)
		{
			// print the exception on the panel
			stackTraceAsString = Strings.toString(exception);
		}
		else
		{
			// rethrow the exception
			throw new WicketRuntimeException(exception);
		}
	}

	/**
	 * Gets whether to escape HTML characters.
	 *
	 * @return whether to escape HTML characters. The default value is false.
	 */
	public void setEscapeHtml(boolean value)
	{
		this.escapeHtml = value;
	}

	/**
	 * Evaluates the template and returns the result.
	 *
	 * @param templateReader
	 *            used to read the template
	 * @return the result of evaluating the velocity template
	 */
	private String evaluateFreemarkerTemplate(Template template)
	{
		if (evaluatedTemplate == null)
		{
			// Get model as a map
			final Map<String, Object> map = (Map<String, Object>)getDefaultModelObject();

			// create a writer for capturing the Velocity output
			StringWriter writer = new StringWriter();

			// string to be used as the template name for log messages in case
			// of error
			try
			{
				// execute the Freemarker script and capture the output in writer
				Freemarker.evaluate(template, map, writer);

				// replace the tag's body the Freemarker output
				evaluatedTemplate = writer.toString();

				if (escapeHtml)
				{
					// encode the result in order to get valid html output that
					// does not break the rest of the page
					evaluatedTemplate = Strings.escapeMarkup(evaluatedTemplate).toString();
				}
				return evaluatedTemplate;
			}
			catch (IOException e)
			{
				onException(e);
			}
			catch (TemplateException e)
			{
				onException(e);
			}
			return null;
		}
		return evaluatedTemplate;
	}

	/**
	 * Gets whether to parse the resulting Wicket markup.
	 *
	 * @return whether to parse the resulting Wicket markup. The default is false.
	 */
	public void setParseGeneratedMarkup(boolean value)
	{
		this.parseGeneratedMarkup = value;
	}

	/**
	 * Whether any Freemarker exception should be trapped and displayed on the panel (false) or thrown
	 * up to be handled by the exception mechanism of Wicket (true). The default is false, which
	 * traps and displays any exception without having consequences for the other components on the
	 * page.
	 * <p>
	 * Trapping these exceptions without disturbing the other components is especially useful in CMS
	 * like applications, where 'normal' users are allowed to do basic scripting. On errors, you
	 * want them to be able to have them correct them while the rest of the application keeps on
	 * working.
	 * </p>
	 *
	 * @return Whether any Freemarker exceptions should be thrown or trapped. The default is false.
	 */
	public void setThrowFreemarkerExceptions(boolean value)
	{
		this.throwFreemarkerExceptions = value;
	}

	/**
	 * @see org.apache.wicket.markup.IMarkupResourceStreamProvider#getMarkupResourceStream(org.apache
	 *      .wicket.MarkupContainer, java.lang.Class)
	 */
	@Override
	public final IResourceStream getMarkupResourceStream(MarkupContainer container,
			Class< ? > containerClass)
	{
		Template template = getTemplate();
		if (template == null)
		{
			throw new WicketRuntimeException("could not find Freemarker template for panel: " + this);
		}

		// evaluate the template and return a new StringResourceStream
		StringBuffer sb = new StringBuffer();
		sb.append("<wicket:panel>");
		sb.append(evaluateFreemarkerTemplate(template));
		sb.append("</wicket:panel>");
		return new StringResourceStream(sb.toString());
	}

	/**
	 * @see org.apache.wicket.markup.IMarkupCacheKeyProvider#getCacheKey(org.apache.wicket.
	 *      MarkupContainer, java.lang.Class)
	 */
	@Override
	public final String getCacheKey(MarkupContainer container, Class< ? > containerClass)
	{
		// don't cache the evaluated template
		return null;
	}

	/**
	 * @see org.apache.wicket.Component#onDetach()
	 */
	@Override
	protected void onDetach()
	{
		super.onDetach();
		stackTraceAsString = null;
		evaluatedTemplate = null;
	}
}
