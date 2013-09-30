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
import java.text.MessageFormat;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.util.RawParseUtils;

import com.gitblit.models.PathModel.PathChangeModel;
import com.gitblit.utils.DiffUtils.DiffStat;

/**
 * Generates an html snippet of a diff in Gitblit's style, tracks changed paths,
 * and calculates diff stats.
 *
 * @author James Moger
 *
 */
public class GitBlitDiffFormatter extends DiffFormatter {

	private final OutputStream os;

	private final DiffStat diffStat;

	private PathChangeModel currentPath;

	private int left, right;

	public GitBlitDiffFormatter(OutputStream os, String commitId) {
		super(os);
		this.os = os;
		this.diffStat = new DiffStat(commitId);
	}

	@Override
	public void format(DiffEntry ent) throws IOException {
		currentPath = diffStat.addPath(ent);
		super.format(ent);
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
		// update entry diffstat
		currentPath.update(prefix);

		// output diff
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
				line = StringUtils.convertOctal(line);
				if (line.indexOf(oldnull) > -1) {
					// a is null, use b
					line = line.substring(("diff --git " + oldnull).length()).trim();
					// trim b/
					line = line.substring(2).trim();
				} else {
					// use a
					line = line.substring("diff --git ".length()).trim();
					line = line.substring(line.startsWith("\"a/") ? 3 : 2);
					line = line.substring(0, line.indexOf(" b/") > -1 ? line.indexOf(" b/") : line.indexOf("\"b/")).trim();
				}

				if (line.charAt(0) == '"') {
					line = line.substring(1);
				}
				if (line.charAt(line.length() - 1) == '"') {
					line = line.substring(0, line.length() - 1);
				}
				if (inFile) {
					sb.append("</tbody></table></div>\n");
					inFile = false;
				}

				sb.append(MessageFormat.format("<div class='header'><div class=\"diffHeader\" id=\"{0}\"><i class=\"icon-file\"></i> ", line)).append(line).append("</div></div>");
				sb.append("<div class=\"diff\">");
				sb.append("<table><tbody>");
				inFile = true;
			} else {
				boolean gitLinkDiff = line.length() > 0 && line.substring(1).startsWith("Subproject commit");
				if (gitLinkDiff) {
					sb.append("<tr><th></th><th></th>");
					if (line.charAt(0) == '+') {
						sb.append("<td><div class=\"diff add2\">");
					} else {
						sb.append("<td><div class=\"diff remove2\">");
					}
				}
				sb.append(line);
				if (gitLinkDiff) {
					sb.append("</div></td></tr>");
				}
			}
		}
		sb.append("</table></div>");
		return sb.toString();
	}

	public DiffStat getDiffStat() {
		return diffStat;
	}
}
