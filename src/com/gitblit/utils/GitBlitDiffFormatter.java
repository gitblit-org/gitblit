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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.util.RawParseUtils;

/**
 * Generates an html snippet of a diff in Gitblit's style.
 * 
 * @author James Moger
 * 
 */
public class GitBlitDiffFormatter extends GitWebDiffFormatter {

	private final OutputStream os;

	private int left, right;

	public GitBlitDiffFormatter(OutputStream os) {
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
		os.write("<tr><th>..</th><th>..</th><td class='hunk_header'>".getBytes());
		os.write('@');
		os.write('@');
		writeRange('-', aStartLine + 1, aEndLine - aStartLine);
		writeRange('+', bStartLine + 1, bEndLine - bStartLine);
		os.write(' ');
		os.write('@');
		os.write('@');
		os.write("</td></tr>\n".getBytes());
		left = aStartLine + 1;
		right = bStartLine + 1;
	}

	@Override
	protected void writeLine(final char prefix, final RawText text, final int cur)
			throws IOException {
		os.write("<tr>".getBytes());
		switch (prefix) {
		case '+':
			os.write(("<th></th><th>" + (right++) + "</th>").getBytes());
			os.write("<td><div class=\"diff add2\">".getBytes());
			break;
		case '-':
			os.write(("<th>" + (left++) + "</th><th></th>").getBytes());
			os.write("<td><div class=\"diff remove2\">".getBytes());
			break;
		default:
			os.write(("<th>" + (left++) + "</th><th>" + (right++) + "</th>").getBytes());
			os.write("<td>".getBytes());
			break;
		}
		os.write(prefix);
		String line = text.getString(cur);
		line = StringUtils.escapeForHtml(line, false);
		os.write(encode(line));
		switch (prefix) {
		case '+':
		case '-':
			os.write("</div>".getBytes());
			break;
		default:
			os.write("</td>".getBytes());
		}
		os.write("</tr>\n".getBytes());
	}

	/**
	 * Workaround function for complex private methods in DiffFormatter. This
	 * sets the html for the diff headers.
	 * 
	 * @return
	 */
	@Override
	public String getHtml() {
		ByteArrayOutputStream bos = (ByteArrayOutputStream) os;
		String html = RawParseUtils.decode(bos.toByteArray());
		String[] lines = html.split("\n");
		StringBuilder sb = new StringBuilder();
		boolean inFile = false;
		String oldnull = "a/dev/null";
		for (String line : lines) {
			if (line.startsWith("index")) {
				// skip index lines
			} else if (line.startsWith("new file")) {
				// skip new file lines
			} else if (line.startsWith("\\ No newline")) {
				// skip no new line
			} else if (line.startsWith("---") || line.startsWith("+++")) {
				// skip --- +++ lines
			} else if (line.startsWith("diff")) {
				if (line.indexOf(oldnull) > -1) {
					// a is null, use b
					line = line.substring(("diff --git " + oldnull).length()).trim();
					// trim b/
					line = line.substring(2);
				} else {
					// use a
					line = line.substring("diff --git a/".length()).trim();
					line = line.substring(0, line.indexOf(" b/")).trim();
				}
				if (inFile) {
					sb.append("</tbody></table></div>\n");
					inFile = false;
				}
				sb.append("<div class='header'>").append(line).append("</div>");
				sb.append("<div class=\"diff\">");
				sb.append("<table><tbody>");
				inFile = true;
			} else {
				sb.append(line);
			}
		}
		sb.append("</table></div>");
		return sb.toString();
	}
}
