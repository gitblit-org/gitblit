package com.gitblit.wicket;

import org.apache.wicket.Component;
import org.apache.wicket.behavior.SimpleAttributeModifier;

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
}
