package com.gitblit.tests.mock;

import java.util.Map;
import java.util.Properties;

import com.gitblit.IStoredSettings;

public class MemorySettings extends IStoredSettings {
	
	private Map<Object, Object> backingMap;
	
	public MemorySettings(Map<Object, Object> backingMap) {
		super(MemorySettings.class);
		this.backingMap = backingMap;
	}

	@Override
	protected Properties read() {
		Properties props = new Properties();
		props.putAll(backingMap);
		
		return props;
	}

	@Override
	public boolean saveSettings(Map<String, String> updatedSettings) {
		return false;
	}

}
