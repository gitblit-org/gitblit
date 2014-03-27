/*
 * Copyright 2014 Jake Wharton
 * Copyright 2014 gitblit.com.
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
package com.gitblit.utils;


/**
 * This is a forked version of FlipTables which supports controlling the
 * displayed borders and gracefully handles null cell values.
 *
 * FULL = all borders
 * BODY_COLS = header + perimeter + column separators
 * COLS = header + column separators
 * BODY = header + perimeter
 * HEADER = header only
 *
 * <pre>
 * ╔═════════════╤════════════════════════════╤══════════════╗
 * ║ Name        │ Function                   │ Author       ║
 * ╠═════════════╪════════════════════════════╪══════════════╣
 * ║ Flip Tables │ Pretty-print a text table. │ Jake Wharton ║
 * ╚═════════════╧════════════════════════════╧══════════════╝
 * </pre>
 */
public final class FlipTable {
	public static final String EMPTY = "(empty)";

	public static enum Borders {
		FULL(15), BODY_HCOLS(13), HCOLS(12), BODY(9), HEADER(8), COLS(4);

		final int bitmask;

		private Borders(int bitmask) {
			this.bitmask = bitmask;
		}

		boolean header() {
			return isset(0x8);
		}

		boolean body() {
			return isset(0x1);
		}

		boolean rows() {
			return isset(0x2);
		}

		boolean columns() {
			return isset(0x4);
		}

		boolean isset(int v) {
			return (bitmask & v) == v;
		}
	}

	/** Create a new table with the specified headers and row data. */
	public static String of(String[] headers, Object[][] data) {
		return of(headers, data, Borders.FULL);
	}

	/** Create a new table with the specified headers and row data. */
	public static String of(String[] headers, Object[][] data, Borders borders) {
		if (headers == null)
			throw new NullPointerException("headers == null");
		if (headers.length == 0)
			throw new IllegalArgumentException("Headers must not be empty.");
		if (data == null)
			throw new NullPointerException("data == null");
		return new FlipTable(headers, data, borders).toString();
	}

	private final String[] headers;
	private final Object[][] data;
	private final Borders borders;
	private final int columns;
	private final int[] columnWidths;
	private final int emptyWidth;

	private FlipTable(String[] headers, Object[][] data, Borders borders) {
		this.headers = headers;
		this.data = data;
		this.borders = borders;

		columns = headers.length;
		columnWidths = new int[columns];
		for (int row = -1; row < data.length; row++) {
			Object[] rowData = (row == -1) ? headers : data[row];
			if (rowData.length != columns) {
				throw new IllegalArgumentException(String.format("Row %s's %s columns != %s columns", row + 1,
						rowData.length, columns));
			}
			for (int column = 0; column < columns; column++) {
				Object cell = rowData[column];
				if (cell == null) {
					continue;
				}
				for (String rowDataLine : cell.toString().split("\\n")) {
					columnWidths[column] = Math.max(columnWidths[column], rowDataLine.length());
				}
			}
		}

		 // Account for column dividers and their spacing.
		int emptyWidth = 3 * (columns - 1);
		for (int columnWidth : columnWidths) {
			emptyWidth += columnWidth;
		}
		this.emptyWidth = emptyWidth;

		if (emptyWidth < EMPTY.length()) {
			// Make sure we're wide enough for the empty text.
			columnWidths[columns - 1] += EMPTY.length() - emptyWidth;
		}
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		if (borders.header()) {
			printDivider(builder, "╔═╤═╗");
		}
		printData(builder, headers, true);
		if (data.length == 0) {
			if (borders.body()) {
				printDivider(builder, "╠═╧═╣");
				builder.append('║').append(pad(emptyWidth, EMPTY)).append("║\n");
				printDivider(builder, "╚═══╝");
			} else if (borders.header()) {
				printDivider(builder, "╚═╧═╝");
				builder.append(' ').append(pad(emptyWidth, EMPTY)).append(" \n");
			}
		} else {
			for (int row = 0; row < data.length; row++) {
				if (row == 0 && borders.header()) {
					if (borders.body()) {
						if (borders.columns()) {
							printDivider(builder, "╠═╪═╣");
						} else {
							printDivider(builder, "╠═╧═╣");
						}
					} else {
						if (borders.columns()) {
							printDivider(builder, "╚═╪═╝");
						} else {
							printDivider(builder, "╚═╧═╝");
						}
					}
				} else if (row == 0 && !borders.header()) {
					if (borders.columns()) {
						printDivider(builder, " ─┼─ ");
					} else {
						printDivider(builder, " ─┼─ ");
					}
				} else if (borders.rows()) {
					if (borders.columns()) {
						printDivider(builder, "╟─┼─╢");
					} else {
						printDivider(builder, "╟─┼─╢");
					}
				}
				printData(builder, data[row], false);
			}
			if (borders.body()) {
				if (borders.columns()) {
					printDivider(builder, "╚═╧═╝");
				} else {
					printDivider(builder, "╚═══╝");
				}
			}
		}
		return builder.toString();
	}

	private void printDivider(StringBuilder out, String format) {
		for (int column = 0; column < columns; column++) {
			out.append(column == 0 ? format.charAt(0) : format.charAt(2));
			out.append(pad(columnWidths[column], "").replace(' ', format.charAt(1)));
		}
		out.append(format.charAt(4)).append('\n');
	}

	private void printData(StringBuilder out, Object[] data, boolean isHeader) {
		for (int line = 0, lines = 1; line < lines; line++) {
			for (int column = 0; column < columns; column++) {
				if (column == 0) {
					if ((isHeader && borders.header()) || borders.body()) {
						out.append('║');
					} else {
						out.append(' ');
					}
				} else if (isHeader || borders.columns()) {
					out.append('│');
				} else {
					out.append(' ');
				}
				Object cell = data[column];
				if (cell == null) {
					cell = "";
				}
				String[] cellLines = cell.toString().split("\\n");
				lines = Math.max(lines, cellLines.length);
				String cellLine = line < cellLines.length ? cellLines[line] : "";
				out.append(pad(columnWidths[column], cellLine));
			}
			if ((isHeader && borders.header()) || borders.body()) {
				out.append("║\n");
			} else {
				out.append('\n');
			}
		}
	}

	private static String pad(int width, String data) {
		return String.format(" %1$-" + width + "s ", data);
	}
}
