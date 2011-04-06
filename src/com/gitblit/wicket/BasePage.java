package com.gitblit.wicket;

import java.util.Date;

import org.apache.wicket.Component;
import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.utils.Utils;


public abstract class BasePage extends WebPage {

	Logger logger = LoggerFactory.getLogger(BasePage.class);

	public BasePage() {
		super();
	}

	public BasePage(PageParameters params) {
		super(params);
	}

	protected Label createAuthorLabel(String wicketId, String author) {
		Label label = new Label(wicketId, author);
		WicketUtils.setHtmlTitle(label, author);
		return label;
	}

	protected Label createDateLabel(String wicketId, Date date) {
		Label label = new Label(wicketId, GitBlitWebSession.get().formatDate(date));
		WicketUtils.setCssClass(label, Utils.timeAgoCss(date));
		WicketUtils.setHtmlTitle(label, Utils.timeAgo(date));
		return label;
	}

	protected Label createShortlogDateLabel(String wicketId, Date date) {
		String dateString = GitBlitWebSession.get().formatDate(date);
		String title = Utils.timeAgo(date);
		if ((System.currentTimeMillis() - date.getTime()) < 10 * 24 * 60 * 60 * 1000l) {
			dateString = title;
			title = GitBlitWebSession.get().formatDate(date);
		}
		Label label = new Label(wicketId, dateString);
		WicketUtils.setCssClass(label, Utils.timeAgoCss(date));
		WicketUtils.setHtmlTitle(label, title);
		return label;
	}

	protected void setAlternatingBackground(Component c, int i) {
		String clazz = i % 2 == 0 ? "dark" : "light";
		WicketUtils.setCssClass(c, clazz);
	}

	protected String trimShortLog(String string) {
		return trimString(string, 60);
	}
	
	protected String trimString(String value, int max) {
		if (value.length() <= max) {
			return value;
		}
		return value.substring(0, max - 3) + "...";
	}

	public void error(String message, Throwable t) {
		super.error(message);
		logger.error(message, t);
	}
}
