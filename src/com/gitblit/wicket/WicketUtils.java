package com.gitblit.wicket;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import org.apache.wicket.Component;
import org.apache.wicket.PageParameters;
import org.apache.wicket.behavior.SimpleAttributeModifier;
import org.apache.wicket.markup.html.basic.Label;

import com.gitblit.StoredSettings;
import com.gitblit.utils.Utils;

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

	public static String breakLines(String string) {
		return string.replace("\r", "<br/>").replace("\n", "<br/>");
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
	
	public static String flattenStrings(List<String> values) {
		StringBuilder sb = new StringBuilder();
		for (String value : values) {
			sb.append(value).append(" ");
		}
		return sb.toString().trim();
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

	public static String trimShortLog(String string) {
		return trimString(string, 60);
	}
	
	public static String trimString(String value, int max) {
		if (value.length() <= max) {
			return value;
		}
		return value.substring(0, max - 3) + "...";
	}
	
	public static PageParameters newRepositoryParameter(String repositoryName) {
		return new PageParameters("r=" + repositoryName);
	}

	public static PageParameters newObjectParameter(String repositoryName, String objectId) {
		if (objectId == null || objectId.trim().length() == 0) {
			return newRepositoryParameter(repositoryName);
		}
		return new PageParameters("r=" + repositoryName + ",h=" + objectId);
	}

	public static PageParameters newPathParameter(String repositoryName, String objectId, String path) {
		if (path == null || path.trim().length() == 0) {
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
	
	public static String getRepositoryName(PageParameters params) {
		return params.getString("r", "");
	}

	public static String getObject(PageParameters params) {
		return params.getString("h", "");
	}

	public static String getPath(PageParameters params) {
		return params.getString("f", null);
	}

	public static int getPage(PageParameters params) {
		return params.getInt("page", 1); // index from 1
	}

	public static Label createDateLabel(String wicketId, Date date, TimeZone timeZone) {
		DateFormat df = new SimpleDateFormat(StoredSettings.getString("datestampShortFormat", "MM/dd/yy"));
		if (timeZone != null) {
			df.setTimeZone(timeZone);
		}
		String dateString = df.format(date);
		String title = Utils.timeAgo(date);
		if ((System.currentTimeMillis() - date.getTime()) < 10 * 24 * 60 * 60 * 1000l) {
			String tmp = dateString;
			dateString = title;
			title = tmp;
		}
		Label label = new Label(wicketId, dateString);
		WicketUtils.setCssClass(label, Utils.timeAgoCss(date));
		WicketUtils.setHtmlTitle(label, title);
		return label;
	}
}
