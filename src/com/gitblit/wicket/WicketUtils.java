package com.gitblit.wicket;

import org.apache.wicket.AttributeModifier;
import org.apache.wicket.Component;
import org.apache.wicket.model.AbstractReadOnlyModel;

public class WicketUtils {

	public static void setCssClass(Component container, String value) {
		container.add(newAttributeModifier("class", value));
	}

	public static void setCssStyle(Component container, String value) {
		container.add(newAttributeModifier("style", value));
	}

	public static void setHtmlTitle(Component container, String value) {
		container.add(newAttributeModifier("title", value));
	}

	private static AttributeModifier newAttributeModifier(String attrib, final String value) {
		return new AttributeModifier(attrib, true, new AbstractReadOnlyModel<String>() {
			private static final long serialVersionUID = 1L;

			@Override
			public String getObject() {
				return value;
			}
		});
	}

	public static String breakLines(String string) {
		return string.replace("\r", "<br/>").replace("\n", "<br/>");
	}
}
