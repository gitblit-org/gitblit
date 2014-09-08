package com.gitblit.wicket;

import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.util.lang.Objects;
import org.parboiled.common.StringUtils;
import org.slf4j.LoggerFactory;

public class SafeTextModel implements IModel<String> {

	private static final long serialVersionUID = 1L;

	public enum Mode {
		relaxed, none
	}

	private final Mode mode;

	private String value;

	public static SafeTextModel none() {
		return new SafeTextModel(Mode.none);
	}

	public static SafeTextModel none(String value) {
		return new SafeTextModel(value, Mode.none);
	}

	public static SafeTextModel relaxed() {
		return new SafeTextModel(Mode.relaxed);
	}

	public static SafeTextModel relaxed(String value) {
		return new SafeTextModel(value, Mode.relaxed);
	}

	public SafeTextModel(Mode mode) {
		this.mode = mode;
	}

	public SafeTextModel(String value, Mode mode) {
		this.value = value;
		this.mode = mode;
	}

	@Override
	public void detach() {
	}

	@Override
	public String getObject() {
		if (StringUtils.isEmpty(value)) {
			return value;
		}
		String safeValue;
		switch (mode) {
		case none:
			safeValue = GitBlitWebApp.get().xssFilter().none(value);
			break;
		default:
			safeValue = GitBlitWebApp.get().xssFilter().relaxed(value);
			break;
		}
		if (!value.equals(safeValue)) {
			LoggerFactory.getLogger(getClass()).warn("XSS filter trigggered on suspicious form field value {}",
					value);
		}
		return safeValue;
	}

	@Override
	public void setObject(String input) {
		this.value = input;
	}

	@Override
	public int hashCode()
	{
		return Objects.hashCode(value);
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
		{
			return true;
		}
		if (!(obj instanceof Model<?>))
		{
			return false;
		}
		Model<?> that = (Model<?>)obj;
		return Objects.equal(value, that.getObject());
	}
}
