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
