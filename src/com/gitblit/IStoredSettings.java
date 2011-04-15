package com.gitblit;

import java.util.List;

public interface IStoredSettings {

	public abstract List<String> getAllKeys(String startingWith);

	public abstract boolean getBoolean(String name, boolean defaultValue);

	public abstract int getInteger(String name, int defaultValue);

	public abstract String getString(String name, String defaultValue);

	public abstract List<String> getStrings(String name);

	public abstract List<String> getStringsFromValue(String value);

	public abstract List<String> getStrings(String name, String separator);

	public abstract List<String> getStringsFromValue(String value, String separator);

}