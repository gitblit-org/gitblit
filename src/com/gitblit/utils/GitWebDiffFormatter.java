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

import static org.eclipse.jgit.lib.Constants.encode;
import static org.eclipse.jgit.lib.Constants.encodeASCII;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.util.RawParseUtils;

/**
 * Returns an html snippet of the diff in the standard Gitweb style.
 * 
 * @author James Moger
 * 
 */
public class GitWebDiffFormatter extends DiffFormatter {

	private final OutputStream os;

	public GitWebDiffFormatter(OutputStream os) {
		super(os);
		this.os = os;
	}

	/**
	 * Output a hunk header
	 * 
	 * @param aStartLine
	 *            within first source
	 * @param aEndLine
	 *            within first source
	 * @param bStartLine
	 *            within second source
	 * @param bEndLine
	 *            within second source
	 * @throws IOException
	 */
	@Override
	protected void writeHunkHeader(int aStartLine, int aEndLine, int bStartLine, int bEndLine)
			throws IOException {
		os.write("<div class=\"diff hunk_header\"><span class=\"diff hunk_info\">".getBytes());
		os.write('@');
		os.write('@');
		writeRange('-', aStartLine + 1, aEndLine - aStartLine);
		writeRange('+', bStartLine + 1, bEndLine - bStartLine);
		os.write(' ');
		os.write('@');
		os.write('@');
		os.write("</span></div>".getBytes());
	}

	protected void writeRange(final char prefix, final int begin, final int cnt) throws IOException {
		os.write(' ');
		os.write(prefix);
		switch (cnt) {
		case 0:
			// If the range is empty, its beginning number must
			// be the
			// line just before the range, or 0 if the range is
			// at the
			// start of the file stream. Here, begin is always 1
			// based,
			// so an empty file would produce "0,0".
			//
			os.write(encodeASCII(begin - 1));
			os.write(',');
			os.write('0');
			break;

		case 1:
			// If the range is exactly one line, produce only
			// the number.
			//
			os.write(encodeASCII(begin));
			break;

		default:
			os.write(encodeASCII(begin));
			os.write(',');
			os.write(encodeASCII(cnt));
			break;
		}
	}

	@Override
	protected void writeLine(final char prefix, final RawText text, final int cur)
			throws IOException {
		switch (prefix) {
		case '+':
			os.write("<span class=\"diff add\">".getBytes());
			break;
		case '-':
			os.write("<span class=\"diff remove\">".getBytes());
			break;
		}
		os.write(prefix);
		String line = text.getString(cur);
		line = StringUtils.escapeForHtml(line, false);
		os.write(encode(line));
		switch (prefix) {
		case '+':
		case '-':
			os.write("</span>\n".getBytes());
			break;
		default:
			os.write('\n');
		}
	}

	/**
	 * Workaround function for complex private methods in DiffFormatter. This
	 * sets the html for the diff headers.
	 * 
	 * @return
	 */
	public String getHtml() {
		ByteArrayOutputStream bos = (ByteArrayOutputStream) os;
		String html = RawParseUtils.decode(bos.toByteArray());
		String[] lines = html.split("\n");
		StringBuilder sb = new StringBuilder();
		sb.append("<div class=\"diff\">");
		for (String line : lines) {
			if (line.startsWith("diff")) {
				sb.append("<div class=\"diff header\">").append(line).append("</div>");
			} else if (line.startsWith("---")) {
				sb.append("<span class=\"diff remove\">").append(line).append("</span><br/>");
			} else if (line.startsWith("+++")) {
				sb.append("<span class=\"diff add\">").append(line).append("</span><br/>");
			} else {
				sb.append(line).append('\n');
			}
		}
		sb.append("</div>\n");
		return sb.toString();
	}
}
