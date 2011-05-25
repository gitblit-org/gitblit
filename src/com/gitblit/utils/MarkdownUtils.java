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

import org.tautua.markdownpapers.Markdown;
import org.tautua.markdownpapers.parser.ParseException;

public class MarkdownUtils {

	public static String transformMarkdown(String markdown) throws java.text.ParseException {
		// Read raw markdown content and transform it to html		
		StringReader reader = new StringReader(markdown);
		StringWriter writer = new StringWriter();
		try {
			Markdown md = new Markdown();
			md.transform(reader, writer);
			return writer.toString();
		} catch (ParseException p) {			
			throw new java.text.ParseException(p.getMessage(), 0);
		} finally {
			reader.close();
			try {
				writer.close();
			} catch (IOException e) {
			}
		}
	}

	public static String transformMarkdown(Reader markdownReader) throws java.text.ParseException {
		// Read raw markdown content and transform it to html				
		StringWriter writer = new StringWriter();
		try {
			Markdown md = new Markdown();
			md.transform(markdownReader, writer);
			return writer.toString();
		} catch (ParseException p) {			
			throw new java.text.ParseException(p.getMessage(), 0);
		} finally {
			try {
				markdownReader.close();
			} catch (IOException e) {
			}
			try {
				writer.close();
			} catch (IOException e) {
			}
		}
	}

}
