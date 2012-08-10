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
package com.gitblit.utils;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;

import org.slf4j.LoggerFactory;
import org.tautua.markdownpapers.Markdown;
import org.tautua.markdownpapers.parser.ParseException;

/**
 * Utility methods for transforming raw markdown text to html.
 * 
 * @author James Moger
 * 
 */
public class MarkdownUtils {

	/**
	 * Returns the html version of the markdown source text.
	 * 
	 * @param markdown
	 * @return html version of markdown text
	 * @throws java.text.ParseException
	 */
	public static String transformMarkdown(String markdown) throws java.text.ParseException {
		try {
			StringReader reader = new StringReader(markdown);
			String html = transformMarkdown(reader);
			reader.close();
			return html;
		} catch (IllegalArgumentException e) {
			throw new java.text.ParseException(e.getMessage(), 0);
		} catch (NullPointerException p) {
			throw new java.text.ParseException("Markdown string is null!", 0);
		}
	}

	/**
	 * Returns the html version of the markdown source reader. The reader is
	 * closed regardless of success or failure.
	 * 
	 * @param markdownReader
	 * @return html version of the markdown text
	 * @throws java.text.ParseException
	 */
	public static String transformMarkdown(Reader markdownReader) throws java.text.ParseException {
		// Read raw markdown content and transform it to html
		StringWriter writer = new StringWriter();
		try {
			Markdown md = new Markdown();
			md.transform(markdownReader, writer);
			return writer.toString().trim();
		} catch (ParseException p) {
			LoggerFactory.getLogger(MarkdownUtils.class).error("MarkdownPapers failed to parse Markdown!", p);
			throw new java.text.ParseException(p.getMessage(), 0);
		} finally {
			try {
				writer.close();
			} catch (IOException e) {
				// IGNORE
			}
		}
	}
}
