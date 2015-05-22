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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.wicket.Application;
import org.apache.wicket.Localizer;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.util.RawParseUtils;

import com.gitblit.models.PathModel.PathChangeModel;
import com.gitblit.utils.DiffUtils.BinaryDiffHandler;
import com.gitblit.utils.DiffUtils.DiffStat;
import com.gitblit.wicket.GitBlitWebApp;

/**
 * Generates an html snippet of a diff in Gitblit's style, tracks changed paths, and calculates diff stats.
 *
 * @author James Moger
 * @author Tom <tw201207@gmail.com>
 *
 */
public class GitBlitDiffFormatter extends DiffFormatter {

	/** Regex pattern identifying trailing whitespace. */
	private static final Pattern trailingWhitespace = Pattern.compile("(\\s+?)\r?\n?$");

	/**
	 * gitblit.properties key for the per-file limit on the number of diff lines.
	 */
	private static final String DIFF_LIMIT_PER_FILE_KEY = "web.maxDiffLinesPerFile";

	/**
	 * gitblit.properties key for the global limit on the number of diff lines in a commitdiff.
	 */
	private static final String GLOBAL_DIFF_LIMIT_KEY = "web.maxDiffLines";

	/**
	 * Diffs with more lines are not shown in commitdiffs. (Similar to what GitHub does.) Can be reduced
	 * (but not increased) through gitblit.properties key {@link #DIFF_LIMIT_PER_FILE_KEY}.
	 */
	private static final int DIFF_LIMIT_PER_FILE = 4000;

	/**
	 * Global diff limit. Commitdiffs with more lines are truncated. Can be reduced (but not increased)
	 * through gitblit.properties key {@link #GLOBAL_DIFF_LIMIT_KEY}.
	 */
	private static final int GLOBAL_DIFF_LIMIT = 20000;

	private static final boolean CONVERT_TABS = true;

	private final DiffOutputStream os;

	private final DiffStat diffStat;

	private PathChangeModel currentPath;

	private int left, right;

	/**
	 * If a single file diff in a commitdiff produces more than this number of lines, we don't display
	 * the diff. First, it's too taxing on the browser: it'll spend an awful lot of time applying the
	 * CSS rules (despite my having optimized them). And second, no human can read a diff with thousands
	 * of lines and make sense of it.
	 * <p>
	 * Set to {@link #DIFF_LIMIT_PER_FILE} for commitdiffs, and to -1 (switches off the limit) for
	 * single-file diffs.
	 * </p>
	 */
	private final int maxDiffLinesPerFile;

	/**
	 * Global limit on the number of diff lines. Set to {@link #GLOBAL_DIFF_LIMIT} for commitdiffs, and
	 * to -1 (switched off the limit) for single-file diffs.
	 */
	private final int globalDiffLimit;

	/** Number of lines for the current file diff. Set to zero when a new DiffEntry is started. */
	private int nofLinesCurrent;
	/**
	 * Position in the stream when we try to write the first line. Used to rewind when we detect that
	 * the diff is too large.
	 */
	private int startCurrent;
	/** Flag set to true when we rewind. Reset to false when we start a new DiffEntry. */
	private boolean isOff;
	/** The current diff entry. */
	private DiffEntry entry;

	// Global limit stuff.

	/** Total number of lines written before the current diff entry. */
	private int totalNofLinesPrevious;
	/** Running total of the number of diff lines written. Updated until we exceed the global limit. */
	private int totalNofLinesCurrent;
	/** Stream position to reset to if we decided to truncate the commitdiff. */
	private int truncateTo;
	/** Whether we decided to truncate the commitdiff. */
	private boolean truncated;
	/** If {@link #truncated}, contains all entries skipped. */
	private final List<DiffEntry> skipped = new ArrayList<DiffEntry>();

	private int tabLength;

	/**
	 * A {@link ResettableByteArrayOutputStream} that intercept the "Binary files differ" message produced
	 * by the super implementation. Unfortunately the super implementation has far too many things private;
	 * otherwise we'd just have re-implemented {@link GitBlitDiffFormatter#format(DiffEntry) format(DiffEntry)}
	 * completely without ever calling the super implementation.
	 */
	private static class DiffOutputStream extends ResettableByteArrayOutputStream {

		private static final String BINARY_DIFFERENCE = "Binary files differ\n";

		private GitBlitDiffFormatter formatter;
		private BinaryDiffHandler binaryDiffHandler;

		public void setFormatter(GitBlitDiffFormatter formatter, BinaryDiffHandler handler) {
			this.formatter = formatter;
			this.binaryDiffHandler = handler;
		}

		@Override
		public void write(byte[] b, int offset, int length) {
			if (binaryDiffHandler != null
					&& RawParseUtils.decode(Arrays.copyOfRange(b, offset, offset + length)).contains(BINARY_DIFFERENCE))
			{
				String binaryDiff = binaryDiffHandler.renderBinaryDiff(formatter.entry);
				if (binaryDiff != null) {
					byte[] bb = ("<tr><td colspan='4' align='center'>" + binaryDiff + "</td></tr>").getBytes(StandardCharsets.UTF_8);
					super.write(bb, 0, bb.length);
					return;
				}
			}
			super.write(b, offset, length);
		}

	}

	public GitBlitDiffFormatter(String commitId, String path, BinaryDiffHandler handler, int tabLength) {
		super(new DiffOutputStream());
		this.os = (DiffOutputStream) getOutputStream();
		this.os.setFormatter(this, handler);
		this.diffStat = new DiffStat(commitId);
		this.tabLength = tabLength;
		// If we have a full commitdiff, install maxima to avoid generating a super-long diff listing that
		// will only tax the browser too much.
		maxDiffLinesPerFile = path != null ? -1 : getLimit(DIFF_LIMIT_PER_FILE_KEY, 500, DIFF_LIMIT_PER_FILE);
		globalDiffLimit = path != null ? -1 : getLimit(GLOBAL_DIFF_LIMIT_KEY, 1000, GLOBAL_DIFF_LIMIT);
	}

	/**
	 * Determines a limit to use for HTML diff output.
	 *
	 * @param key
	 *            to use to read the value from the GitBlit settings, if available.
	 * @param minimum
	 *            minimum value to enforce
	 * @param maximum
	 *            maximum (and default) value to enforce
	 * @return the limit
	 */
	private int getLimit(String key, int minimum, int maximum) {
		if (Application.exists()) {
			Application application = Application.get();
			if (application instanceof GitBlitWebApp) {
				GitBlitWebApp webApp = (GitBlitWebApp) application;
				int configValue = webApp.settings().getInteger(key, maximum);
				if (configValue < minimum) {
					return minimum;
				} else if (configValue < maximum) {
					return configValue;
				}
			}
		}
		return maximum;
	}

	/**
	 * Returns a localized message string, if there is a localization; otherwise the given default value.
	 *
	 * @param key
	 *            message key for the message
	 * @param defaultValue
	 *            to use if no localization for the message can be found
	 * @return the possibly localized message
	 */
	private String getMsg(String key, String defaultValue) {
		if (Application.exists()) {
			Localizer localizer = Application.get().getResourceSettings().getLocalizer();
			if (localizer != null) {
				// Use getStringIgnoreSettings because we don't want exceptions here if the key is missing!
				return localizer.getStringIgnoreSettings(key, null, null, defaultValue);
			}
		}
		return defaultValue;
	}

	@Override
	public void format(DiffEntry ent) throws IOException {
		currentPath = diffStat.addPath(ent);
		nofLinesCurrent = 0;
		isOff = false;
		entry = ent;
		if (!truncated) {
			totalNofLinesPrevious = totalNofLinesCurrent;
			if (globalDiffLimit > 0 && totalNofLinesPrevious > globalDiffLimit) {
				truncated = true;
				isOff = true;
			}
			truncateTo = os.size();
		} else {
			isOff = true;
		}
		if (truncated) {
			skipped.add(ent);
		} else {
			// Produce a header here and now
			String path;
			String id;
			if (ChangeType.DELETE.equals(ent.getChangeType())) {
				path = ent.getOldPath();
				id = ent.getOldId().name();
			} else {
				path = ent.getNewPath();
				id = ent.getNewId().name();
			}
			StringBuilder sb = new StringBuilder(MessageFormat.format("<div class='header'><div class=\"diffHeader\" id=\"n{0}\"><i class=\"icon-file\"></i> ", id));
			sb.append(StringUtils.escapeForHtml(path, false)).append("</div></div>");
			sb.append("<div class=\"diff\"><table cellpadding='0'><tbody>\n");
			os.write(sb.toString().getBytes());
		}
		// Keep formatting, but if off, don't produce anything anymore. We just keep on counting.
		super.format(ent);
		if (!truncated) {
			// Close the table
			os.write("</tbody></table></div>\n".getBytes());
		}
	}

	@Override
	public void flush() throws IOException {
		if (truncated) {
			os.resetTo(truncateTo);
		}
		super.flush();
	}

	/**
	 * Rewind and issue a message that the diff is too large.
	 */
	private void reset() {
		if (!isOff) {
			os.resetTo(startCurrent);
			writeFullWidthLine(getMsg("gb.diffFileDiffTooLarge", "Diff too large"));
			totalNofLinesCurrent = totalNofLinesPrevious;
			isOff = true;
		}
	}

	/**
	 * Writes an initial table row containing information about added/removed/renamed/copied files. In case
	 * of a deletion, we also suppress generating the diff; it's not interesting. (All lines removed.)
	 */
	private void handleChange() {
		// XXX Would be nice if we could generate blob links for the cases handled here. Alas, we lack the repo
		// name, and cannot reliably determine it here. We could get the .git directory of a Repository, if we
		// passed in the repo, and then take the name of the parent directory, but that'd fail for repos nested
		// in GitBlit projects. And we don't know if the repo is inside a project or is a top-level repo.
		//
		// That's certainly solvable (just pass along more information), but would require a larger rewrite than
		// I'm prepared to do now.
		String message;
		switch (entry.getChangeType()) {
		case ADD:
			message = getMsg("gb.diffNewFile", "New file");
			break;
		case DELETE:
			message = getMsg("gb.diffDeletedFile", "File was deleted");
			isOff = true;
			break;
		case RENAME:
			message = MessageFormat.format(getMsg("gb.diffRenamedFile", "File was renamed from {0}"), entry.getOldPath());
			break;
		case COPY:
			message = MessageFormat.format(getMsg("gb.diffCopiedFile", "File was copied from {0}"), entry.getOldPath());
			break;
		default:
			return;
		}
		writeFullWidthLine(message);
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
	protected void writeHunkHeader(int aStartLine, int aEndLine, int bStartLine, int bEndLine) throws IOException {
		if (nofLinesCurrent++ == 0) {
			handleChange();
			startCurrent = os.size();
		}
		if (!isOff) {
			totalNofLinesCurrent++;
			if (nofLinesCurrent > maxDiffLinesPerFile && maxDiffLinesPerFile > 0) {
				reset();
			} else {
				os.write("<tr><th class='diff-line' data-lineno='..'></th><th class='diff-line' data-lineno='..'></th><th class='diff-state'></th><td class='hunk_header'>"
						.getBytes());
				os.write('@');
				os.write('@');
				writeRange('-', aStartLine + 1, aEndLine - aStartLine);
				writeRange('+', bStartLine + 1, bEndLine - bStartLine);
				os.write(' ');
				os.write('@');
				os.write('@');
				os.write("</td></tr>\n".getBytes());
			}
		}
		left = aStartLine + 1;
		right = bStartLine + 1;
	}

	protected void writeRange(final char prefix, final int begin, final int cnt) throws IOException {
		os.write(' ');
		os.write(prefix);
		switch (cnt) {
		case 0:
			// If the range is empty, its beginning number must be the
			// line just before the range, or 0 if the range is at the
			// start of the file stream. Here, begin is always 1 based,
			// so an empty file would produce "0,0".
			//
			os.write(encodeASCII(begin - 1));
			os.write(',');
			os.write('0');
			break;

		case 1:
			// If the range is exactly one line, produce only the number.
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

	/**
	 * Writes a line spanning the full width of the code view, including the gutter.
	 *
	 * @param text
	 *            to put on that line; will be HTML-escaped.
	 */
	private void writeFullWidthLine(String text) {
		try {
			os.write("<tr><td class='diff-cell' colspan='4'>".getBytes());
			os.write(StringUtils.escapeForHtml(text, false).getBytes());
			os.write("</td></tr>\n".getBytes());
		} catch (IOException ex) {
			// Cannot happen with a ByteArrayOutputStream
		}
	}

	@Override
	protected void writeLine(final char prefix, final RawText text, final int cur) throws IOException {
		if (nofLinesCurrent++ == 0) {
			handleChange();
			startCurrent = os.size();
		}
		// update entry diffstat
		currentPath.update(prefix);
		if (isOff) {
			return;
		}
		totalNofLinesCurrent++;
		if (nofLinesCurrent > maxDiffLinesPerFile && maxDiffLinesPerFile > 0) {
			reset();
		} else {
			// output diff
			os.write("<tr>".getBytes());
			switch (prefix) {
			case '+':
				os.write(("<th class='diff-line'></th><th class='diff-line' data-lineno='" + (right++) + "'></th>").getBytes());
				os.write("<th class='diff-state diff-state-add'></th>".getBytes());
				os.write("<td class='diff-cell add2'>".getBytes());
				break;
			case '-':
				os.write(("<th class='diff-line' data-lineno='" + (left++) + "'></th><th class='diff-line'></th>").getBytes());
				os.write("<th class='diff-state diff-state-sub'></th>".getBytes());
				os.write("<td class='diff-cell remove2'>".getBytes());
				break;
			default:
				os.write(("<th class='diff-line' data-lineno='" + (left++) + "'></th><th class='diff-line' data-lineno='" + (right++) + "'></th>").getBytes());
				os.write("<th class='diff-state'></th>".getBytes());
				os.write("<td class='diff-cell context2'>".getBytes());
				break;
			}
			os.write(encode(codeLineToHtml(prefix, text.getString(cur))));
			os.write("</td></tr>\n".getBytes());
		}
	}

	/**
	 * Convert the given code line to HTML.
	 *
	 * @param prefix
	 *            the diff prefix (+/-) indicating whether the line was added or removed.
	 * @param line
	 *            the line to format as HTML
	 * @return the HTML-formatted line, safe for inserting as is into HTML.
	 */
	private String codeLineToHtml(final char prefix, final String line) {
		if ((prefix == '+' || prefix == '-')) {
			// Highlight trailing whitespace on deleted/added lines.
			Matcher matcher = trailingWhitespace.matcher(line);
			if (matcher.find()) {
				StringBuilder result = new StringBuilder(StringUtils.escapeForHtml(line.substring(0, matcher.start()), CONVERT_TABS, tabLength));
				result.append("<span class='trailingws-").append(prefix == '+' ? "add" : "sub").append("'>");
				result.append(StringUtils.escapeForHtml(matcher.group(1), false));
				result.append("</span>");
				return result.toString();
			}
		}
		return StringUtils.escapeForHtml(line, CONVERT_TABS, tabLength);
	}

	/**
	 * Workaround function for complex private methods in DiffFormatter. This sets the html for the diff headers.
	 *
	 * @return
	 */
	public String getHtml() {
		String html = RawParseUtils.decode(os.toByteArray());
		String[] lines = html.split("\n");
		StringBuilder sb = new StringBuilder();
		for (String line : lines) {
			if (line.startsWith("index") || line.startsWith("similarity")
					|| line.startsWith("rename from ") || line.startsWith("rename to ")) {
				// skip index lines
			} else if (line.startsWith("new file") || line.startsWith("deleted file")) {
				// skip new file lines
			} else if (line.startsWith("\\ No newline")) {
				// skip no new line
			} else if (line.startsWith("---") || line.startsWith("+++")) {
				// skip --- +++ lines
			} else if (line.startsWith("diff")) {
				// skip diff lines
			} else {
				boolean gitLinkDiff = line.length() > 0 && line.substring(1).startsWith("Subproject commit");
				if (gitLinkDiff) {
					sb.append("<tr><th class='diff-line'></th><th class='diff-line'></th>");
					if (line.charAt(0) == '+') {
						sb.append("<th class='diff-state diff-state-add'></th><td class=\"diff-cell add2\">");
					} else {
						sb.append("<th class='diff-state diff-state-sub'></th><td class=\"diff-cell remove2\">");
					}
					line = StringUtils.escapeForHtml(line.substring(1), CONVERT_TABS, tabLength);
				}
				sb.append(line);
				if (gitLinkDiff) {
					sb.append("</td></tr>");
				}
				sb.append('\n');
			}
		}
		if (truncated) {
			sb.append(MessageFormat.format("<div class='header'><div class='diffHeader'>{0}</div></div>",
					StringUtils.escapeForHtml(getMsg("gb.diffTruncated", "Diff truncated after the above file"), false)));
			// List all files not shown. We can be sure we do have at least one path in skipped.
			sb.append("<div class='diff'><table cellpadding='0'><tbody><tr><td class='diff-cell' colspan='4'>");
			String deletedSuffix = StringUtils.escapeForHtml(getMsg("gb.diffDeletedFileSkipped", "(deleted)"), false);
			boolean first = true;
			for (DiffEntry entry : skipped) {
				if (!first) {
					sb.append('\n');
				}
				if (ChangeType.DELETE.equals(entry.getChangeType())) {
					sb.append("<span id=\"n" + entry.getOldId().name() + "\">" + StringUtils.escapeForHtml(entry.getOldPath(), false) + ' ' + deletedSuffix + "</span>");
				} else {
					sb.append("<span id=\"n" + entry.getNewId().name() + "\">" + StringUtils.escapeForHtml(entry.getNewPath(), false) + "</span>");
				}
				first = false;
			}
			skipped.clear();
			sb.append("</td></tr></tbody></table></div>");
		}
		return sb.toString();
	}

	public DiffStat getDiffStat() {
		return diffStat;
	}
}
