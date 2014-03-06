/*
 * Copyright 2007 Daniel Spiewak.
 * Copyright 2013 gitblit.com.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gitblit.wicket.charting;

/**
 * This class is a pristine fork of org.wicketstuff.googlecharts.ChartDataEncoding
 * to bring the package-protected convert methods to SecureChart.
 *
 * @author Daniel Spiewak
 */
public enum SecureChartDataEncoding {

    SIMPLE("s", "", ",") {

        @Override
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

    	@Override
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

    	@Override
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

    private SecureChartDataEncoding(String rendering, String valueSeparator, String setSeparator) {
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
