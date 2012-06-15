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

import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import javax.servlet.http.HttpServletRequest;

import org.apache.wicket.Component;
import org.apache.wicket.PageParameters;
import org.apache.wicket.Request;
import org.apache.wicket.behavior.HeaderContributor;
import org.apache.wicket.behavior.SimpleAttributeModifier;
import org.apache.wicket.markup.html.IHeaderContributor;
import org.apache.wicket.markup.html.IHeaderResponse;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.image.ContextImage;
import org.apache.wicket.protocol.http.WebRequest;
import org.apache.wicket.resource.ContextRelativeResource;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.wicketstuff.googlecharts.AbstractChartData;
import org.wicketstuff.googlecharts.IChartData;

import com.gitblit.Constants;
import com.gitblit.Constants.FederationPullStatus;
import com.gitblit.GitBlit;
import com.gitblit.Keys;
import com.gitblit.models.FederationModel;
import com.gitblit.models.Metric;
import com.gitblit.utils.HttpUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.utils.TimeUtils;

public class WicketUtils {

	public static void setCssClass(Component container, String value) {
		container.add(new SimpleAttributeModifier("class", value));
	}

	public static void setCssStyle(Component container, String value) {
		container.add(new SimpleAttributeModifier("style", value));
	}

	public static void setCssBackground(Component container, String value) {
		String background = MessageFormat.format("background-color:{0};",
				StringUtils.getColor(value));
		container.add(new SimpleAttributeModifier("style", background));
	}

	public static void setHtmlTooltip(Component container, String value) {
		container.add(new SimpleAttributeModifier("title", value));
	}

	public static void setInputPlaceholder(Component container, String value) {
		container.add(new SimpleAttributeModifier("placeholder", value));
	}

	public static void setChangeTypeCssClass(Component container, ChangeType type) {
		switch (type) {
		case ADD:
			setCssClass(container, "addition");
			break;
		case COPY:
		case RENAME:
			setCssClass(container, "rename");
			break;
		case DELETE:
			setCssClass(container, "deletion");
			break;
		case MODIFY:
			setCssClass(container, "modification");
			break;
		}
	}

	public static void setTicketCssClass(Component container, String state) {
		String css = null;
		if (state.equals("open")) {
			css = "label label-important";
		} else if (state.equals("hold")) {
			css = "label label-warning";
		} else if (state.equals("resolved")) {
			css = "label label-success";
		} else if (state.equals("invalid")) {
			css = "label";
		}
		if (css != null) {
			setCssClass(container, css);
		}
	}

	public static void setAlternatingBackground(Component c, int i) {
		String clazz = i % 2 == 0 ? "light" : "dark";
		setCssClass(c, clazz);
	}

	public static Label createAuthorLabel(String wicketId, String author) {
		Label label = new Label(wicketId, author);
		WicketUtils.setHtmlTooltip(label, author);
		return label;
	}

	public static ContextImage getPullStatusImage(String wicketId, FederationPullStatus status) {
		String filename = null;
		switch (status) {
		case MIRRORED:
		case PULLED:
			filename = "bullet_green.png";
			break;
		case SKIPPED:
			filename = "bullet_yellow.png";
			break;
		case FAILED:
			filename = "bullet_red.png";
			break;
		case EXCLUDED:
			filename = "bullet_white.png";
			break;
		case PENDING:
		case NOCHANGE:
		default:
			filename = "bullet_black.png";
		}
		return WicketUtils.newImage(wicketId, filename, status.name());
	}

	public static ContextImage getFileImage(String wicketId, String filename) {
		filename = filename.toLowerCase();
		if (filename.endsWith(".java")) {
			return newImage(wicketId, "file_java_16x16.png");
		} else if (filename.endsWith(".rb")) {
			return newImage(wicketId, "file_ruby_16x16.png");
		} else if (filename.endsWith(".php")) {
			return newImage(wicketId, "file_php_16x16.png");
		} else if (filename.endsWith(".cs")) {
			return newImage(wicketId, "file_cs_16x16.png");
		} else if (filename.endsWith(".cpp")) {
			return newImage(wicketId, "file_cpp_16x16.png");
		} else if (filename.endsWith(".c")) {
			return newImage(wicketId, "file_c_16x16.png");
		} else if (filename.endsWith(".h")) {
			return newImage(wicketId, "file_h_16x16.png");
		} else if (filename.endsWith(".sln")) {
			return newImage(wicketId, "file_vs_16x16.png");
		} else if (filename.endsWith(".csv") || filename.endsWith(".xls")
				|| filename.endsWith(".xlsx")) {
			return newImage(wicketId, "file_excel_16x16.png");
		} else if (filename.endsWith(".doc") || filename.endsWith(".docx")) {
			return newImage(wicketId, "file_word_16x16.png");
		} else if (filename.endsWith(".ppt")) {
			return newImage(wicketId, "file_ppt_16x16.png");
		} else if (filename.endsWith(".zip")) {
			return newImage(wicketId, "file_zip_16x16.png");
		} else if (filename.endsWith(".pdf")) {
			return newImage(wicketId, "file_acrobat_16x16.png");
		} else if (filename.endsWith(".htm") || filename.endsWith(".html")) {
			return newImage(wicketId, "file_world_16x16.png");
		} else if (filename.endsWith(".xml")) {
			return newImage(wicketId, "file_code_16x16.png");
		} else if (filename.endsWith(".properties")) {
			return newImage(wicketId, "file_settings_16x16.png");
		}

		List<String> mdExtensions = GitBlit.getStrings(Keys.web.markdownExtensions);
		for (String ext : mdExtensions) {
			if (filename.endsWith('.' + ext.toLowerCase())) {
				return newImage(wicketId, "file_world_16x16.png");
			}
		}
		return newImage(wicketId, "file_16x16.png");
	}

	public static ContextImage getRegistrationImage(String wicketId, FederationModel registration,
			Component c) {
		if (registration.isResultData()) {
			return WicketUtils.newImage(wicketId, "information_16x16.png",
					c.getString("gb.federationResults"));
		} else {
			return WicketUtils.newImage(wicketId, "arrow_left.png",
					c.getString("gb.federationRegistration"));
		}
	}

	public static ContextImage newClearPixel(String wicketId) {
		return newImage(wicketId, "pixel.png");
	}

	public static ContextImage newBlankImage(String wicketId) {
		return newImage(wicketId, "blank.png");
	}

	public static ContextImage newImage(String wicketId, String file) {
		return newImage(wicketId, file, null);
	}

	public static ContextImage newImage(String wicketId, String file, String tooltip) {
		ContextImage img = new ContextImage(wicketId, file);
		if (!StringUtils.isEmpty(tooltip)) {
			setHtmlTooltip(img, tooltip);
		}
		return img;
	}

	public static Label newIcon(String wicketId, String css) {
		Label lbl = new Label(wicketId);
		setCssClass(lbl, css);		
		return lbl;
	}
	
	public static Label newBlankIcon(String wicketId) {
		Label lbl = new Label(wicketId);
		setCssClass(lbl, "");
		lbl.setRenderBodyOnly(true);
		return lbl;
	}
	
	public static ContextRelativeResource getResource(String file) {
		return new ContextRelativeResource(file);
	}

	public static String getGitblitURL(Request request) {
		HttpServletRequest req = ((WebRequest) request).getHttpServletRequest();
		return HttpUtils.getGitblitURL(req);
	}

	public static HeaderContributor syndicationDiscoveryLink(final String feedTitle,
			final String url) {
		return new HeaderContributor(new IHeaderContributor() {
			private static final long serialVersionUID = 1L;

			public void renderHead(IHeaderResponse response) {
				String contentType = "application/rss+xml";

				StringBuffer buffer = new StringBuffer();
				buffer.append("<link rel=\"alternate\" ");
				buffer.append("type=\"").append(contentType).append("\" ");
				buffer.append("title=\"").append(feedTitle).append("\" ");
				buffer.append("href=\"").append(url).append("\" />");
				response.renderString(buffer.toString());
			}
		});
	}

	public static PageParameters newTokenParameter(String token) {
		return new PageParameters("t=" + token);
	}

	public static PageParameters newRegistrationParameter(String url, String name) {
		return new PageParameters("u=" + url + ",n=" + name);
	}

	public static PageParameters newUsernameParameter(String username) {
		return new PageParameters("user=" + username);
	}

	public static PageParameters newTeamnameParameter(String teamname) {
		return new PageParameters("team=" + teamname);
	}

	public static PageParameters newRepositoryParameter(String repositoryName) {
		return new PageParameters("r=" + repositoryName);
	}

	public static PageParameters newObjectParameter(String objectId) {
		return new PageParameters("h=" + objectId);
	}

	public static PageParameters newObjectParameter(String repositoryName, String objectId) {
		if (StringUtils.isEmpty(objectId)) {
			return newRepositoryParameter(repositoryName);
		}
		return new PageParameters("r=" + repositoryName + ",h=" + objectId);
	}

	public static PageParameters newPathParameter(String repositoryName, String objectId,
			String path) {
		if (StringUtils.isEmpty(path)) {
			return newObjectParameter(repositoryName, objectId);
		}
		if (StringUtils.isEmpty(objectId)) {
			return new PageParameters("r=" + repositoryName + ",f=" + path);
		}
		return new PageParameters("r=" + repositoryName + ",h=" + objectId + ",f=" + path);
	}

	public static PageParameters newLogPageParameter(String repositoryName, String objectId,
			int pageNumber) {
		if (pageNumber <= 1) {
			return newObjectParameter(repositoryName, objectId);
		}
		if (StringUtils.isEmpty(objectId)) {
			return new PageParameters("r=" + repositoryName + ",pg=" + pageNumber);
		}
		return new PageParameters("r=" + repositoryName + ",h=" + objectId + ",pg=" + pageNumber);
	}

	public static PageParameters newHistoryPageParameter(String repositoryName, String objectId,
			String path, int pageNumber) {
		if (pageNumber <= 1) {
			return newObjectParameter(repositoryName, objectId);
		}
		if (StringUtils.isEmpty(objectId)) {
			return new PageParameters("r=" + repositoryName + ",f=" + path + ",pg=" + pageNumber);
		}
		return new PageParameters("r=" + repositoryName + ",h=" + objectId + ",f=" + path + ",pg="
				+ pageNumber);
	}

	public static PageParameters newBlobDiffParameter(String repositoryName, String baseCommitId,
			String commitId, String path) {
		if (StringUtils.isEmpty(commitId)) {
			return new PageParameters("r=" + repositoryName + ",f=" + path + ",hb=" + baseCommitId);
		}
		return new PageParameters("r=" + repositoryName + ",h=" + commitId + ",f=" + path + ",hb="
				+ baseCommitId);
	}

	public static PageParameters newSearchParameter(String repositoryName, String commitId,
			String search, Constants.SearchType type) {
		if (StringUtils.isEmpty(commitId)) {
			return new PageParameters("r=" + repositoryName + ",s=" + search + ",st=" + type.name());
		}
		return new PageParameters("r=" + repositoryName + ",h=" + commitId + ",s=" + search
				+ ",st=" + type.name());
	}

	public static PageParameters newSearchParameter(String repositoryName, String commitId,
			String search, Constants.SearchType type, int pageNumber) {
		if (StringUtils.isEmpty(commitId)) {
			return new PageParameters("r=" + repositoryName + ",s=" + search + ",st=" + type.name()
					+ ",pg=" + pageNumber);
		}
		return new PageParameters("r=" + repositoryName + ",h=" + commitId + ",s=" + search
				+ ",st=" + type.name() + ",pg=" + pageNumber);
	}

	public static String getRepositoryName(PageParameters params) {
		return params.getString("r", "");
	}

	public static String getObject(PageParameters params) {
		return params.getString("h", null);
	}

	public static String getPath(PageParameters params) {
		return params.getString("f", null);
	}

	public static String getBaseObjectId(PageParameters params) {
		return params.getString("hb", null);
	}

	public static String getSearchString(PageParameters params) {
		return params.getString("s", null);
	}

	public static String getSearchType(PageParameters params) {
		return params.getString("st", null);
	}

	public static int getPage(PageParameters params) {
		// index from 1
		return params.getInt("pg", 1);
	}

	public static String getRegEx(PageParameters params) {
		return params.getString("x", "");
	}

	public static String getSet(PageParameters params) {
		return params.getString("set", "");
	}

	public static String getTeam(PageParameters params) {
		return params.getString("team", "");
	}

	public static int getDaysBack(PageParameters params) {
		return params.getInt("db", 14);
	}

	public static String getUsername(PageParameters params) {
		return params.getString("user", "");
	}

	public static String getTeamname(PageParameters params) {
		return params.getString("team", "");
	}

	public static String getToken(PageParameters params) {
		return params.getString("t", "");
	}

	public static String getUrlParameter(PageParameters params) {
		return params.getString("u", "");
	}

	public static String getNameParameter(PageParameters params) {
		return params.getString("n", "");
	}

	public static Label createDateLabel(String wicketId, Date date, TimeZone timeZone, TimeUtils timeUtils) {
		String format = GitBlit.getString(Keys.web.datestampShortFormat, "MM/dd/yy");
		DateFormat df = new SimpleDateFormat(format);
		if (timeZone == null) {
			timeZone = GitBlit.getTimezone();
		}
		df.setTimeZone(timeZone);
		String dateString;
		if (date.getTime() == 0) {
			dateString = "--";
		} else {
			dateString = df.format(date);
		}
		String title = null;
		if (date.getTime() <= System.currentTimeMillis()) {
			// past
			title = timeUtils.timeAgo(date);
		}
		if ((System.currentTimeMillis() - date.getTime()) < 10 * 24 * 60 * 60 * 1000L) {
			String tmp = dateString;
			dateString = title;
			title = tmp;
		}
		Label label = new Label(wicketId, dateString);
		WicketUtils.setCssClass(label, timeUtils.timeAgoCss(date));
		if (!StringUtils.isEmpty(title)) {
			WicketUtils.setHtmlTooltip(label, title);
		}
		return label;
	}

	public static Label createTimeLabel(String wicketId, Date date, TimeZone timeZone, TimeUtils timeUtils) {
		String format = GitBlit.getString(Keys.web.timeFormat, "HH:mm");
		DateFormat df = new SimpleDateFormat(format);
		if (timeZone == null) {
			timeZone = GitBlit.getTimezone();
		}
		df.setTimeZone(timeZone);
		String timeString;
		if (date.getTime() == 0) {
			timeString = "--";
		} else {
			timeString = df.format(date);
		}
		String title = timeUtils.timeAgo(date);
		Label label = new Label(wicketId, timeString);
		if (!StringUtils.isEmpty(title)) {
			WicketUtils.setHtmlTooltip(label, title);
		}
		return label;
	}

	public static Label createDatestampLabel(String wicketId, Date date, TimeZone timeZone, TimeUtils timeUtils) {
		String format = GitBlit.getString(Keys.web.datestampLongFormat, "EEEE, MMMM d, yyyy");
		DateFormat df = new SimpleDateFormat(format);
		if (timeZone == null) {
			timeZone = GitBlit.getTimezone();
		}
		df.setTimeZone(timeZone);
		String dateString;
		if (date.getTime() == 0) {
			dateString = "--";
		} else {
			dateString = df.format(date);
		}
		String title = null;
		if (TimeUtils.isToday(date)) {
			title = timeUtils.today();
		} else if (TimeUtils.isYesterday(date)) {
				title = timeUtils.yesterday();
		} else if (date.getTime() <= System.currentTimeMillis()) {
			// past
			title = timeUtils.timeAgo(date);
		}
		if ((System.currentTimeMillis() - date.getTime()) < 10 * 24 * 60 * 60 * 1000L) {
			String tmp = dateString;
			dateString = title;
			title = tmp;
		}
		Label label = new Label(wicketId, dateString);
		if (!StringUtils.isEmpty(title)) {
			WicketUtils.setHtmlTooltip(label, title);
		}
		return label;
	}

	public static Label createTimestampLabel(String wicketId, Date date, TimeZone timeZone, TimeUtils timeUtils) {
		String format = GitBlit.getString(Keys.web.datetimestampLongFormat,
				"EEEE, MMMM d, yyyy HH:mm Z");
		DateFormat df = new SimpleDateFormat(format);
		if (timeZone == null) {
			timeZone = GitBlit.getTimezone();
		}
		df.setTimeZone(timeZone);
		String dateString;
		if (date.getTime() == 0) {
			dateString = "--";
		} else {
			dateString = df.format(date);
		}
		String title = null;
		if (date.getTime() <= System.currentTimeMillis()) {
			// past
			title = timeUtils.timeAgo(date);
		}
		Label label = new Label(wicketId, dateString);
		if (!StringUtils.isEmpty(title)) {
			WicketUtils.setHtmlTooltip(label, title);
		}
		return label;
	}

	public static IChartData getChartData(Collection<Metric> metrics) {
		final double[] commits = new double[metrics.size()];
		final double[] tags = new double[metrics.size()];
		int i = 0;
		double max = 0;
		for (Metric m : metrics) {
			commits[i] = m.count;
			if (m.tag > 0) {
				tags[i] = m.count;
			} else {
				tags[i] = -1d;
			}
			max = Math.max(max, m.count);
			i++;
		}
		IChartData data = new AbstractChartData(max) {
			private static final long serialVersionUID = 1L;

			public double[][] getData() {
				return new double[][] { commits, tags };
			}
		};
		return data;
	}

	public static double maxValue(Collection<Metric> metrics) {
		double max = Double.MIN_VALUE;
		for (Metric m : metrics) {
			if (m.count > max) {
				max = m.count;
			}
		}
		return max;
	}

	public static IChartData getScatterData(Collection<Metric> metrics) {
		final double[] y = new double[metrics.size()];
		final double[] x = new double[metrics.size()];
		int i = 0;
		double max = 0;
		for (Metric m : metrics) {
			y[i] = m.count;
			if (m.duration > 0) {
				x[i] = m.duration;
			} else {
				x[i] = -1d;
			}
			max = Math.max(max, m.count);
			i++;
		}
		IChartData data = new AbstractChartData(max) {
			private static final long serialVersionUID = 1L;

			public double[][] getData() {
				return new double[][] { x, y };
			}
		};
		return data;
	}

}
