/*
 * Created on Dec 11, 2007
 */
package com.codecommit.wicket;


/**
 * @author Daniel Spiewak
 */
public enum ChartDataEncoding {
	SIMPLE("s", "", ",") {
		CharSequence convert(double value, double max) {
			if (value < 0) {
				return "_";
			}
			
			value = Math.round((CHARS.length() - 1) * value / max);
			
			if (value > CHARS.length() - 1) {
				throw new IllegalArgumentException(value + " is out of range for SIMPLE encoding");
			}
			
			return Character.toString(CHARS.charAt((int) value));
		}
	},
	TEXT("t", ",", "|") {
		CharSequence convert(double value, double max) {
			if (value < 0) {
				value = -1;
			}
			
			if (value > 100) {
				throw new IllegalArgumentException(value + " is out of range for TEXT encoding");
			}
			
			return Double.toString(value);
		}
	},
	EXTENDED("e", "", ",") {
		CharSequence convert(double value, double max) {
			if (value < 0) {
				return "__";
			}
			
			value = Math.round(value);
			
			if (value > (EXT_CHARS.length() - 1) * (EXT_CHARS.length() - 1)) {
				throw new IllegalArgumentException(value + " is out of range for EXTENDED encoding");
			}
			
			int rem = (int) (value % EXT_CHARS.length());
			int exp = (int) (value / EXT_CHARS.length());
			
			return new StringBuilder().append(EXT_CHARS.charAt(exp)).append(EXT_CHARS.charAt(rem));
		}
	};
	
	private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
	private static final String EXT_CHARS = CHARS + "-_.";
	
	private final String rendering, valueSeparator, setSeparator;
	
	private ChartDataEncoding(String rendering, String valueSeparator, String setSeparator) {
		this.rendering = rendering;
		this.valueSeparator = valueSeparator;
		this.setSeparator = setSeparator;
	}
	
	public String getRendering() {
		return rendering;
	}
	
	public String getValueSeparator() {
		return valueSeparator;
	}
	
	public String getSetSeparator() {
		return setSeparator;
	}
	
	abstract CharSequence convert(double value, double max);
}
