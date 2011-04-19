package com.gitblit.wicket;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import org.apache.wicket.Component;
import org.apache.wicket.PageParameters;
import org.apache.wicket.behavior.SimpleAttributeModifier;
import org.apache.wicket.markup.html.basic.Label;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.lib.Constants;

import com.gitblit.GitBlit;
import com.gitblit.Keys;
import com.gitblit.utils.JGitUtils.SearchType;
import com.gitblit.utils.StringUtils;
import com.gitblit.utils.TimeUtils;

public class WicketUtils {

	public static void setCssClass(Component container, String value) {
		container.add(new SimpleAttributeModifier("class", value));
	}

	public static void setCssStyle(Component container, String value) {
		container.add(new SimpleAttributeModifier("style", value));
	}

	public static void setHtmlTitle(Component container, String value) {
		container.add(new SimpleAttributeModifier("title", value));
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
			css = "bug_open";
		} else if (state.equals("hold")) {
			css = "bug_hold";
		} else if (state.equals("resolved")) {
			css = "bug_resolved";
		} else if (state.equals("invalid")) {
			css = "bug_invalid";
		}
		if (css != null) {
			setCssClass(container, css);
		}
	}

	public static void setAlternatingBackground(Component c, int i) {
		String clazz = i % 2 == 0 ? "dark" : "light";
		setCssClass(c, clazz);
	}

	public static Label createAuthorLabel(String wicketId, String author) {
		Label label = new Label(wicketId, author);
		WicketUtils.setHtmlTitle(label, author);
		return label;
	}

	public static PageParameters newRepositoryParameter(String repositoryName) {
		return new PageParameters("r=" + repositoryName);
	}

	public static PageParameters newObjectParameter(String repositoryName, String objectId) {
		if (StringUtils.isEmpty(objectId)) {
			return newRepositoryParameter(repositoryName);
		}
		return new PageParameters("r=" + repositoryName + ",h=" + objectId);
	}

	public static PageParameters newPathParameter(String repositoryName, String objectId, String path) {
		if (StringUtils.isEmpty(path)) {
			return newObjectParameter(repositoryName, objectId);
		}
		return new PageParameters("r=" + repositoryName + ",h=" + objectId + ",f=" + path);
	}

	public static PageParameters newLogPageParameter(String repositoryName, String objectId, int pageNumber) {
		if (pageNumber <= 1) {
			return newObjectParameter(repositoryName, objectId);
		}
		return new PageParameters("r=" + repositoryName + ",h=" + objectId + ",page=" + pageNumber);
	}

	public static PageParameters newHistoryPageParameter(String repositoryName, String objectId, String path, int pageNumber) {
		if (pageNumber <= 1) {
			return newObjectParameter(repositoryName, objectId);
		}
		return new PageParameters("r=" + repositoryName + ",h=" + objectId + ",f=" + path + ",page=" + pageNumber);
	}

	public static PageParameters newBlobDiffParameter(String repositoryName, String baseCommitId, String commitId, String path) {
		return new PageParameters("r=" + repositoryName + ",h=" + commitId + ",f=" + path + ",hb=" + baseCommitId);
	}

	public static PageParameters newSearchParameter(String repositoryName, String commitId, String search, SearchType type) {
		if (StringUtils.isEmpty(commitId)) {
			return new PageParameters("r=" + repositoryName + ",s=" + search + ",st=" + type.name());
		}
		return new PageParameters("r=" + repositoryName + ",h=" + commitId + ",s=" + search + ",st=" + type.name());
	}

	public static PageParameters newSearchParameter(String repositoryName, String commitId, String search, SearchType type, int pageNumber) {
		return new PageParameters("r=" + repositoryName + ",h=" + commitId + ",s=" + search + ",st=" + type.name() + ",page=" + pageNumber);
	}

	public static String getRepositoryName(PageParameters params) {
		return params.getString("r", "");
	}

	public static String getObject(PageParameters params) {
		return params.getString("h", Constants.HEAD);
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
		return params.getInt("page", 1); // index from 1
	}

	public static Label createDateLabel(String wicketId, Date date, TimeZone timeZone) {
		DateFormat df = new SimpleDateFormat(GitBlit.self().settings().getString(Keys.web.datestampShortFormat, "MM/dd/yy"));
		if (timeZone != null) {
			df.setTimeZone(timeZone);
		}
		String dateString = df.format(date);
		String title = TimeUtils.timeAgo(date);
		if ((System.currentTimeMillis() - date.getTime()) < 10 * 24 * 60 * 60 * 1000l) {
			String tmp = dateString;
			dateString = title;
			title = tmp;
		}
		Label label = new Label(wicketId, dateString);
		WicketUtils.setCssClass(label, TimeUtils.timeAgoCss(date));
		WicketUtils.setHtmlTitle(label, title);
		return label;
	}

	public static Label createTimestampLabel(String wicketId, Date date, TimeZone timeZone) {
		DateFormat df = new SimpleDateFormat(GitBlit.self().settings().getString(Keys.web.datetimestampLongFormat, "EEEE, MMMM d, yyyy h:mm a z"));
		if (timeZone != null) {
			df.setTimeZone(timeZone);
		}
		String dateString = df.format(date);
		String title = TimeUtils.timeAgo(date);
		Label label = new Label(wicketId, dateString);
		WicketUtils.setHtmlTitle(label, title);
		return label;
	}
}
