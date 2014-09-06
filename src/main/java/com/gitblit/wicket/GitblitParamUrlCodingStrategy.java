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
package com.gitblit.wicket;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.wicket.IRequestTarget;
import org.apache.wicket.Page;
import org.apache.wicket.protocol.http.request.WebRequestCodingStrategy;
import org.apache.wicket.request.RequestParameters;
import org.apache.wicket.request.target.coding.MixedParamUrlCodingStrategy;
import org.apache.wicket.util.string.AppendingStringBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.utils.XssFilter;

/**
 * Simple subclass of mixed parameter url coding strategy that works around the
 * encoded forward-slash issue that is present in some servlet containers.
 *
 * https://issues.apache.org/jira/browse/WICKET-1303
 * http://tomcat.apache.org/security-6.html
 *
 * @author James Moger
 *
 */
public class GitblitParamUrlCodingStrategy extends MixedParamUrlCodingStrategy {

	private final String[] parameterNames;

	private Logger logger = LoggerFactory.getLogger(GitblitParamUrlCodingStrategy.class);

	private IStoredSettings settings;

	private XssFilter xssFilter;

	/**
	 * Construct.
	 *
	 * @param <C>
	 * @param mountPath
	 *            mount path (not empty)
	 * @param bookmarkablePageClass
	 *            class of mounted page (not null)
	 * @param parameterNames
	 *            the parameter names (not null)
	 */
	public <C extends Page> GitblitParamUrlCodingStrategy(
			IStoredSettings settings,
			XssFilter xssFilter,
			String mountPath,
			Class<C> bookmarkablePageClass, String[] parameterNames) {

		super(mountPath, bookmarkablePageClass, parameterNames);
		this.parameterNames = parameterNames;
		this.settings = settings;
		this.xssFilter = xssFilter;
	}

	/**
	 * Url encodes a string that is mean for a URL path (e.g., between slashes)
	 *
	 * @param string
	 *            string to be encoded
	 * @return encoded string
	 */
	@Override
	protected String urlEncodePathComponent(String string) {
		char altChar = settings.getChar(Keys.web.forwardSlashCharacter, '/');
		if (altChar != '/') {
			string = string.replace('/', altChar);
		}
		return super.urlEncodePathComponent(string);
	}

	/**
	 * Returns a decoded value of the given value (taken from a URL path
	 * section)
	 *
	 * @param value
	 * @return Decodes the value
	 */
	@Override
	protected String urlDecodePathComponent(String value) {
		char altChar = settings.getChar(Keys.web.forwardSlashCharacter, '/');
		if (altChar != '/') {
			value = value.replace(altChar, '/');
		}
		return super.urlDecodePathComponent(value);
	}

	/**
	 * Gets the decoded request target.
	 *
	 * @param requestParameters
	 *            the request parameters
	 * @return the decoded request target
	 */
	@Override
	public IRequestTarget decode(RequestParameters requestParameters) {
		Map<String, Object> parameterMap = (Map<String, Object>) requestParameters.getParameters();
		for (Map.Entry<String, Object> entry : parameterMap.entrySet()) {
			String parameter = entry.getKey();
			if (parameter.startsWith(WebRequestCodingStrategy.NAME_SPACE)) {
				// ignore Wicket parameters
				continue;
			}

			// sanitize Gitblit request parameters
			Object o = entry.getValue();
			if (o instanceof String) {
				String value = o.toString();
				String safeValue = xssFilter.none(value);
				if (!value.equals(safeValue)) {
					logger.warn("XSS filter triggered on {} URL parameter: {}={}",
							getMountPath(), parameter, value);
					parameterMap.put(parameter, safeValue);
				}
			} else if (o instanceof String[]) {
				String[] values = (String[]) o;
				for (int i = 0; i < values.length; i++) {
					String value = values[i].toString();
					String safeValue = xssFilter.none(value);
					if (!value.equals(safeValue)) {
						logger.warn("XSS filter triggered on {} URL parameter: {}={}",
								getMountPath(), parameter, value);
						values[i] = safeValue;
					}
				}
			}
		}

		return super.decode(requestParameters);
	}

	/**
	 * @see org.apache.wicket.request.target.coding.AbstractRequestTargetUrlCodingStrategy#appendParameters(org.apache.wicket.util.string.AppendingStringBuffer,
	 *      java.util.Map)
	 */
	@Override
	protected void appendParameters(AppendingStringBuffer url, Map<String, ?> parameters)
	{
		if (!url.endsWith("/"))
		{
			url.append("/");
		}

		Set<String> parameterNamesToAdd = new HashSet<String>(parameters.keySet());

		// Find index of last specified parameter
		boolean foundParameter = false;
		int lastSpecifiedParameter = parameterNames.length;
		while (lastSpecifiedParameter != 0 && !foundParameter)
		{
			foundParameter = parameters.containsKey(parameterNames[--lastSpecifiedParameter]);
		}

		if (foundParameter)
		{
			for (int i = 0; i <= lastSpecifiedParameter; i++)
			{
				String parameterName = parameterNames[i];
				final Object param = parameters.get(parameterName);
				String value = param instanceof String[] ? ((String[])param)[0] : ((param == null)
					? null : param.toString());
				if (value == null)
				{
					value = "";
				}
				if (!url.endsWith("/"))
				{
					url.append("/");
				}
				url.append(urlEncodePathComponent(value));
				parameterNamesToAdd.remove(parameterName);
			}
		}

		if (!parameterNamesToAdd.isEmpty())
		{
			boolean first = true;
			for (String parameterName : parameterNamesToAdd)
			{
				final Object param = parameters.get(parameterName);
				if (param instanceof String[]) {
					String [] values = (String[]) param;
					for (String value : values) {
						url.append(first ? '?' : '&');
						url.append(urlEncodeQueryComponent(parameterName)).append("=").append(
								urlEncodeQueryComponent(value));
						first = false;
					}
				} else {
					url.append(first ? '?' : '&');
					String value = String.valueOf(param);
					url.append(urlEncodeQueryComponent(parameterName)).append("=").append(
						urlEncodeQueryComponent(value));
				}
				first = false;
			}
		}
	}
}