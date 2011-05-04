package com.gitblit;

import java.util.List;

import javax.servlet.ServletContext;

public class WebXmlSettings implements IStoredSettings {

	public WebXmlSettings(ServletContext context) {
		
	}
	
	@Override
	public List<String> getAllKeys(String startingWith) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean getBoolean(String name, boolean defaultValue) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public int getInteger(String name, int defaultValue) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public String getString(String name, String defaultValue) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<String> getStrings(String name) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<String> getStringsFromValue(String value) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<String> getStrings(String name, String separator) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<String> getStringsFromValue(String value, String separator) {
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public String toString() {
		return "WEB.XML";
	}
}
